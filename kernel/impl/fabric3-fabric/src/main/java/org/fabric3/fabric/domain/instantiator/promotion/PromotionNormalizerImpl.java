/*
 * Fabric3
 * Copyright (c) 2009-2015 Metaform Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * Portions originally based on Apache Tuscany 2007
 * licensed under the Apache 2.0 license.
 */
package org.fabric3.fabric.domain.instantiator.promotion;

import javax.xml.namespace.QName;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.fabric3.api.model.type.component.AbstractReference;
import org.fabric3.api.model.type.component.Autowire;
import org.fabric3.api.model.type.component.BindingDefinition;
import org.fabric3.api.model.type.component.CompositeImplementation;
import org.fabric3.api.model.type.component.Multiplicity;
import org.fabric3.fabric.domain.instantiator.InstantiationContext;
import org.fabric3.fabric.domain.instantiator.PromotionNormalizer;
import org.fabric3.spi.model.instance.LogicalBinding;
import org.fabric3.spi.model.instance.LogicalComponent;
import org.fabric3.spi.model.instance.LogicalCompositeComponent;
import org.fabric3.spi.model.instance.LogicalReference;
import org.fabric3.spi.model.instance.LogicalService;
import org.fabric3.spi.model.instance.LogicalWire;
import org.fabric3.spi.util.UriHelper;

/**
 * Default implementation of the PromotionNormalizer. <p/> The service promotion normalization algorithm works as follows: <li>A reverse-ordered list of
 * services is constructed by walking the service promotion hierarchy from a leaf component to the domain component. The leaf service is added as the last list
 * entry. <p/> <li>The list is iterated in order, starting with the service nearest the domain level. <p/> <li>For each entry, bindings are added or replaced
 * (according to the override setting for the service), policies added, a service contract set if not defined, and the leaf component set as the leaf parent.
 * <li> <p/> </ul> The reference promotion algorithm works as follows: <li> A reverse-ordered list of references is constructed by walking the reference
 * promotion hierarchy from a leaf component to the domain component. The leaf reference is added as the last list entry. <p/> <li>The list is iterated in
 * order, starting with the reference nearest the domain level. <p/> <li>For each entry, bindings are added or replaced (according to the override setting for
 * the reference), policies added and a service contract set if not defined <p/> <li>The list is iterated a second time and wires for references are examined
 * with their targets pushed down to the next (child) level in the hierarchy.
 */
public class PromotionNormalizerImpl implements PromotionNormalizer {

    /**
     * Bootstrap constructor.
     */
    public PromotionNormalizerImpl() {
    }

    public void normalize(LogicalComponent<?> component, InstantiationContext context) {
        normalizeServicePromotions(component, context);
        normalizeReferenceAndWirePromotions(component, context);
    }

    private void normalizeServicePromotions(LogicalComponent<?> component, InstantiationContext context) {
        for (LogicalService service : component.getServices()) {
            LinkedList<LogicalService> services = new LinkedList<>();
            // add the leaf service as the last element
            services.add(service);
            getPromotionHierarchy(service, services);
            if (services.isEmpty()) {
                continue;
            }
            processServicePromotions(services, context);
        }
    }

    private void normalizeReferenceAndWirePromotions(LogicalComponent<?> component, InstantiationContext context) {
        for (LogicalReference reference : component.getReferences()) {
            LinkedList<LogicalReference> references = new LinkedList<>();
            // add the leaf (promoted) reference as the last element
            references.add(reference);
            getPromotionHierarchy(reference, references);
            if (references.isEmpty()) {
                continue;
            }
            processReferencePromotions(references, context);
            processWirePromotions(references, context);
        }
    }

    /**
     * Processes the service promotion hierarchy by updating bindings, policies, and the service contract.
     *
     * @param services the sorted service promotion hierarchy
     * @param context  the instantiation  context
     */
    private void processServicePromotions(LinkedList<LogicalService> services, InstantiationContext context) {
        if (services.size() < 2) {
            // no promotion evaluation needed
            return;
        }
        LogicalService leafService = services.getLast();
        LogicalComponent<?> leafComponent = leafService.getParent();
        List<LogicalBinding<?>> bindings = new ArrayList<>();
        List<LogicalBinding<?>> callbackBindings = new ArrayList<>();

        for (LogicalService service : services) {
            // TODO determine if bindings should be overriden - for now, override
            if (service.getBindings().isEmpty()) {
                service.overrideBindings(bindings);
                service.overrideCallbackBindings(callbackBindings);
            } else {
                bindings = new ArrayList<>();
                bindings.addAll(service.getBindings());
                callbackBindings = new ArrayList<>();
                callbackBindings.addAll(service.getCallbackBindings());
            }
            service.setLeafComponent(leafComponent);
            service.setLeafService(leafService);
        }

    }

    /**
     * Processes the reference promotion hierarchy by updating bindings, policies, and the service contract.
     *
     * @param references the sorted reference promotion hierarchy
     * @param context    the instantiation  context
     */
    @SuppressWarnings({"unchecked"})
    private void processReferencePromotions(LinkedList<LogicalReference> references, InstantiationContext context) {
        if (references.size() < 2) {
            // no promotion evaluation needed
            return;
        }
        LogicalReference leafReference = references.getLast();
        List<LogicalBinding<?>> bindings = new ArrayList<>();
        Autowire autowire = Autowire.INHERITED;

        for (LogicalReference reference : references) {
            AbstractReference referenceDefinition = reference.getDefinition();
            if (referenceDefinition.getAutowire() == Autowire.INHERITED) {
                reference.setAutowire(autowire);
            } else {
                autowire = referenceDefinition.getAutowire();
            }
            // TODO determine if bindings should be overriden - for now, override
            if (reference.getBindings().isEmpty()) {
                List<LogicalBinding<?>> newBindings = new ArrayList<>();

                for (LogicalBinding<?> binding : bindings) {
                    // create a new logical binding based on the promoted one
                    BindingDefinition definition = binding.getDefinition();
                    QName deployable = binding.getDeployable();
                    LogicalBinding<?> newBinding = new LogicalBinding(definition, reference, deployable);
                    newBindings.add(newBinding);
                }
                reference.overrideBindings(newBindings);
            } else {
                bindings = new ArrayList<>();
                bindings.addAll(reference.getBindings());
            }
            reference.setLeafReference(leafReference);
        }
    }

    /**
     * Processes the wiring hierarchy by pushing wires down to child components.
     *
     * @param references the sorted reference promotion hierarchy
     * @param context    the instantiation context
     */
    // TODO handle wire addition
    private void processWirePromotions(LinkedList<LogicalReference> references, InstantiationContext context) {
        if (references.size() < 2) {
            // no promotion evaluation needed
            return;
        }
        List<LogicalService> newTargets = new ArrayList<>();

        for (LogicalReference reference : references) {
            LogicalCompositeComponent composite = reference.getParent().getParent();
            for (LogicalWire wire : reference.getWires()) {
                // TODO support wire overrides
                LogicalService target = wire.getTarget();
                newTargets.add(target);
            }
            if (!newTargets.isEmpty()) {
                List<LogicalWire> newWires = new ArrayList<>();
                for (LogicalService target : newTargets) {
                    QName deployable = composite.getDeployable();
                    LogicalWire newWire = new LogicalWire(reference.getParent(), reference, target, deployable);
                    newWires.add(newWire);
                }
                composite.overrideWires(reference, newWires);
                // TODO if override, new targets should be erased
                //                newTargets = new ArrayList<LogicalService>();
            }
            if (!validateMultiplicity(reference, newTargets, context)) {
                return;
            }
        }

    }

    /**
     * Validates that the reference multiplicity is not violated by reference targets inherited through a promotion hierarchy.
     *
     * @param reference the reference to validate
     * @param targets   the targets specified in the promotion hierarchy
     * @param context   the context
     * @return true if the validation was successful
     */
    private boolean validateMultiplicity(LogicalReference reference, List<LogicalService> targets, InstantiationContext context) {
        if (reference.getParent().getAutowire() == Autowire.ON || !reference.getBindings().isEmpty() || reference.getAutowire() == Autowire.ON
            || reference.getComponentReference() != null) {     // Reference should not be configured in the component.
            // If it is (i.e. getComponentReference() != null, avoid check and return true.
            return true;
        }
        Multiplicity multiplicity = reference.getDefinition().getMultiplicity();
        switch (multiplicity) {
            case ONE_N:
                if (targets.size() < 1) {
                    URI referenceName = reference.getUri();
                    InvalidNumberOfTargets error = new InvalidNumberOfTargets("At least one target must be configured for reference: " + referenceName,
                                                                              reference);
                    context.addError(error);
                    return false;
                }
                return true;
            case ONE_ONE:
                if (targets.size() < 1) {
                    URI referenceName = reference.getUri();
                    InvalidNumberOfTargets error = new InvalidNumberOfTargets(
                            "At least one target must be configured for reference " + "(no targets configured): " + referenceName, reference);
                    context.addError(error);
                    return false;
                } else if (targets.size() > 1) {
                    URI referenceName = reference.getUri();
                    InvalidNumberOfTargets error = new InvalidNumberOfTargets(
                            "Only one target must be configured for reference " + "(multiple targets configured via promotions): " + referenceName, reference);
                    context.addError(error);
                    return false;
                }
                return true;

            case ZERO_N:
                return true;
            case ZERO_ONE:
                if (targets.size() > 1) {
                    URI referenceName = reference.getUri();
                    InvalidNumberOfTargets error = new InvalidNumberOfTargets(
                            "At most one target must be configured for reference " + "(multiple targets configured via promotions): " + referenceName,
                            reference);
                    context.addError(error);
                    return false;
                }
                return true;
        }
        return true;
    }

    /**
     * Updates the list of services with the promotion hierarchy for the given service. The list is populated in reverse order so that the leaf (promoted)
     * service is stored last.
     *
     * @param service  the current service to ascend from
     * @param services the list
     */
    private void getPromotionHierarchy(LogicalService service, LinkedList<LogicalService> services) {
        LogicalComponent<CompositeImplementation> parent = service.getParent().getParent();
        URI serviceUri = service.getUri();
        for (LogicalService promotion : parent.getServices()) {
            URI targetUri = promotion.getPromotedUri();
            if (targetUri.getFragment() == null) {
                // no service specified
                if (targetUri.equals(UriHelper.getDefragmentedName(serviceUri))) {
                    services.addFirst(promotion);
                    if (parent.getParent() != null) {
                        getPromotionHierarchy(promotion, services);
                    }
                }

            } else {
                if (targetUri.equals(serviceUri)) {
                    services.addFirst(promotion);
                    if (parent.getParent() != null) {
                        getPromotionHierarchy(promotion, services);
                    }
                }
            }
        }
    }

    /**
     * Updates the list of references with the promotion hierarchy for the given reference. The list is populated in reverse order so that the leaf (promoted)
     * reference is stored last.
     *
     * @param reference  the current service to ascend from
     * @param references the list
     */
    private void getPromotionHierarchy(LogicalReference reference, LinkedList<LogicalReference> references) {
        URI referenceUri = reference.getUri();
        LogicalComponent<CompositeImplementation> parent = reference.getParent().getParent();
        for (LogicalReference promotion : parent.getReferences()) {
            List<URI> promotedUris = promotion.getPromotedUris();
            for (URI promotedUri : promotedUris) {
                if (promotedUri.getFragment() == null) {
                    if (promotedUri.equals(UriHelper.getDefragmentedName(referenceUri))) {
                        references.addFirst(promotion);
                        if (parent.getParent() != null) {
                            getPromotionHierarchy(promotion, references);
                        }
                    }
                } else {
                    if (promotedUri.equals(referenceUri)) {
                        references.addFirst(promotion);
                        if (parent.getParent() != null) {
                            getPromotionHierarchy(promotion, references);
                        }
                    }
                }
            }

        }
    }

}
