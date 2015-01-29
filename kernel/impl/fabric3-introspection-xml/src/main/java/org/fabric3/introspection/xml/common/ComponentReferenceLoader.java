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
 *
 * Portions originally based on Apache Tuscany 2007
 * licensed under the Apache 2.0 license.
 */
package org.fabric3.introspection.xml.common;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.fabric3.api.annotation.Source;
import org.fabric3.api.model.type.ModelObject;
import org.fabric3.api.model.type.component.Autowire;
import org.fabric3.api.model.type.component.BindingDefinition;
import org.fabric3.api.model.type.component.ComponentReference;
import org.fabric3.api.model.type.component.Multiplicity;
import org.fabric3.api.model.type.component.Target;
import org.fabric3.api.model.type.contract.ServiceContract;
import org.fabric3.spi.introspection.IntrospectionContext;
import org.fabric3.spi.introspection.xml.InvalidTargetException;
import org.fabric3.spi.introspection.xml.InvalidValue;
import org.fabric3.spi.introspection.xml.LoaderHelper;
import org.fabric3.spi.introspection.xml.LoaderRegistry;
import org.fabric3.spi.introspection.xml.UnrecognizedElement;
import org.oasisopen.sca.annotation.Property;
import org.oasisopen.sca.annotation.Reference;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static org.oasisopen.sca.Constants.SCA_NS;

/**
 * Loads a component reference configuration.
 */
public class ComponentReferenceLoader extends AbstractExtensibleTypeLoader<ComponentReference> {
    private static final QName REFERENCE = new QName(SCA_NS, "reference");
    private static final QName CALLBACK = new QName(SCA_NS, "callback");

    private LoaderHelper loaderHelper;
    private boolean roundTrip;

    public ComponentReferenceLoader(@Reference LoaderRegistry registry, @Reference LoaderHelper loaderHelper) {
        super(registry);
        addAttributes("name", "autowire", "target", "multiplicity", "requires", "policySets", "nonOverridable");
        this.loaderHelper = loaderHelper;
    }

    @Property(required = false)
    @Source("$systemConfig/f3:loader/@round.trip")
    public void setRoundTrip(boolean roundTrip) {
        this.roundTrip = roundTrip;
    }

    public QName getXMLType() {
        return REFERENCE;
    }

    public ComponentReference load(XMLStreamReader reader, IntrospectionContext context) throws XMLStreamException {
        Location startLocation = reader.getLocation();

        String name = reader.getAttributeValue(null, "name");
        if (name == null) {
            MissingReferenceName failure = new MissingReferenceName(startLocation);
            context.addError(failure);
            return null;
        }

        String autowire = reader.getAttributeValue(null, "autowire");

        Multiplicity multiplicity = parseMultiplicity(reader, startLocation, context);

        ComponentReference reference = new ComponentReference(name, multiplicity);
        if ("true".equalsIgnoreCase(autowire)) {
            reference.setAutowire(Autowire.ON);
        } else if ("false".equalsIgnoreCase(autowire)) {
            reference.setAutowire(Autowire.OFF);
        }


        String targetAttribute = parseTargets(reference, reader, startLocation, context);

        String nonOverridable = reader.getAttributeValue(null, "nonOverridable");
        if (nonOverridable != null) {
            reference.setNonOverridable(Boolean.parseBoolean(nonOverridable));
        }
        validateAttributes(reader, context, reference);

        if (roundTrip) {
            reference.enableRoundTrip();
            //noinspection VariableNotUsedInsideIf
            if (autowire != null) {
                reference.attributeSpecified("autowire");

            }
            //noinspection VariableNotUsedInsideIf
            if (targetAttribute != null) {
                reference.attributeSpecified("target");
            }
            //noinspection VariableNotUsedInsideIf
            if (nonOverridable != null) {
                reference.attributeSpecified("nonOverridable");
            }
        }

        boolean callback = false;
        boolean bindingError = false;  // used to avoid reporting multiple binding errors
        while (true) {

            switch (reader.next()) {
            case START_ELEMENT:
                Location location = reader.getLocation();
                callback = CALLBACK.equals(reader.getName());
                if (callback) {
                    reader.nextTag();
                }
                QName elementName = reader.getName();
                ModelObject type = registry.load(reader, ModelObject.class, context);
                if (type instanceof ServiceContract) {
                    reference.setServiceContract((ServiceContract) type);
                } else if (type instanceof BindingDefinition) {
                    BindingDefinition binding = (BindingDefinition) type;
                    if (!reference.getTargets().isEmpty()) {
                        if (!bindingError) {
                            // bindings cannot be configured on references if the @target attribute is used
                            InvalidBinding error =
                                    new InvalidBinding("Bindings cannot be configured when the target attribute on a reference is used: "
                                                               + name, location, binding);
                            context.addError(error);
                            bindingError = true;
                        }
                        continue;
                    }
                    configureBinding(reference, binding, callback, location, context);
                } else if (type == null) {
                    // no type, continue processing
                    continue;
                } else {
                    UnrecognizedElement failure = new UnrecognizedElement(reader, location, reference);
                    context.addError(failure);
                    continue;
                }
                if (!reader.getName().equals(elementName) || reader.getEventType() != END_ELEMENT) {
                    throw new AssertionError("Loader must position the cursor to the end element");
                }
                break;
            case END_ELEMENT:
                if (callback) {
                    callback = false;
                    break;
                }
                if (!REFERENCE.equals(reader.getName())) {
                    continue;
                }
                return reference;
            }
        }
    }

    private Multiplicity parseMultiplicity(XMLStreamReader reader, Location location, IntrospectionContext context) {
        String value = reader.getAttributeValue(null, "multiplicity");
        Multiplicity multiplicity = null;
        try {
            if (value != null) {
                multiplicity = Multiplicity.fromString(value);
            }
        } catch (IllegalArgumentException e) {
            InvalidValue failure = new InvalidValue("Invalid multiplicity value: " + value, location);
            context.addError(failure);
        }
        return multiplicity;
    }

    private String parseTargets(ComponentReference reference, XMLStreamReader reader, Location location, IntrospectionContext context) {
        String targetAttribute = reader.getAttributeValue(null, "target");
        List<Target> targets = new ArrayList<>();
        try {
            if (targetAttribute != null) {
                StringTokenizer tokenizer = new StringTokenizer(targetAttribute);
                while (tokenizer.hasMoreTokens()) {
                    String token = tokenizer.nextToken();
                    Target target = loaderHelper.parseTarget(token, reader);
                    targets.add(target);
                }
            }
        } catch (InvalidTargetException e) {
            InvalidValue failure = new InvalidValue("Invalid target format", location, e);
            context.addError(failure);
        }
        reference.addTargets(targets);
        return targetAttribute;
    }

    private void configureBinding(ComponentReference reference,
                                  BindingDefinition binding,
                                  boolean callback,
                                  Location location,
                                  IntrospectionContext context) {
        if (callback) {
            if (binding.getName() == null) {
                // set the default binding name
                BindingHelper.configureName(binding, reference.getCallbackBindings(), location, context);
            }
            reference.addCallbackBinding(binding);
        } else {
            if (binding.getName() == null) {
                // set the default binding name
                BindingHelper.configureName(binding, reference.getBindings(), location, context);
            }

            boolean check = BindingHelper.checkDuplicateNames(binding, reference.getBindings(), location, context);
            if (check) {
                reference.addBinding(binding);
            }
        }
    }

}
