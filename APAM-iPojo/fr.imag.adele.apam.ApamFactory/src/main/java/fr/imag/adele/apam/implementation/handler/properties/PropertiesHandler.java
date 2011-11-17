package fr.imag.adele.apam.implementation.handler.properties;

import java.util.Dictionary;

import org.apache.felix.ipojo.ConfigurationException;
import org.apache.felix.ipojo.architecture.ComponentTypeDescription;
import org.apache.felix.ipojo.architecture.PropertyDescription;
import org.apache.felix.ipojo.metadata.Element;

import fr.imag.adele.apam.apamImpl.CST;
import fr.imag.adele.apam.implementation.ApamFactory;
import fr.imag.adele.apam.implementation.ImplementationHandler;

/**
 * This class handle property declarations for APAM implementations.
 * 
 * @author vega
 * 
 */
public class PropertiesHandler extends ImplementationHandler {

    /**
     * Configuration element to handle APAM properties
     */
    private final static String PROPERTIES_DECLARATION   = "properties";

    /**
     * Configuration property to specify the shared property
     */
    private final static String PROPERTY_SHARED_PROPERTY = "shared";

    /**
     * Configuration property to specify the scope property
     */
    private final static String PROPERTY_SCOPE_PROPERTY  = "scope";

    /**
     * Configuration element to handle APAM user defined property
     */
    private final static String PROPERTY_DECLARATION     = "property";

    /**
     * Configuration property to specify the user defined property's name
     */
    private final static String PROPERTY_NAME_PROPERTY   = "name";

    /**
     * Configuration property to specify the user defined property's type
     */
    private final static String PROPERTY_TYPE_PROPERTY   = "type";

    /**
     * Configuration property to specify the user defined property's value
     */
    private final static String PROPERTY_VALUE_PROPERTY  = "value";

    /**
     * Utility method to add a new property to the implementation description
     */
    private static final void addProperty(ApamFactory.Description implementationDescription, String name,
            String value, String type) {
        implementationDescription.addProperty(new PropertyDescription(name, type, value, true));
    }

    private static final void addProperty(ApamFactory.Description implementationDescription, String name,
            String value) {
        PropertiesHandler.addProperty(implementationDescription, name, value, String.class.getName());
    }

    /**
     * 
     * Initializes the implementation description to include APAM properties, and user defined properties.
     * 
     * @see org.apache.felix.ipojo.Handler#initializeComponentFactory(org.apache.felix.ipojo.architecture.
     *      ComponentTypeDescription, org.apache.felix.ipojo.metadata.Element)
     */
    @Override
    public void initializeComponentFactory(ComponentTypeDescription componentDescriptor, Element componentMetadata)
            throws ConfigurationException {

        /*
         * This handler works only for APAM native implementations
         */
        if (!(componentDescriptor instanceof ApamFactory.Description))
            throw new ConfigurationException("APAM properties handler can only be used on APAM native implementtaions "
                    + componentMetadata);

        ApamFactory.Description implementationDescription = (ApamFactory.Description) componentDescriptor;
        String implementationName = implementationDescription.getName();

        /*
         * Statically validate the implementation properties and add them to the implementation description.
         */

        Element propertiesDeclarations[] = componentMetadata.getElements(PropertiesHandler.PROPERTIES_DECLARATION,
                ImplementationHandler.APAM_NAMESPACE);

        if (!ImplementationHandler.isSingleton(propertiesDeclarations))
            throw new ConfigurationException("APAM properties "
                    + implementationName + ": "
                    + "a single properties declaration must be specified");

        Element propertiesDeclaration = ImplementationHandler.singleton(propertiesDeclarations);

        /*
         * Handle APAM specific properties
         */
        String shared = ImplementationHandler.booleanValue(propertiesDeclaration
                .getAttribute(PropertiesHandler.PROPERTY_SHARED_PROPERTY));
        if (shared != null)
            PropertiesHandler.addProperty(implementationDescription, CST.A_SHARED, shared);

        String scope = ImplementationHandler.visibilityValue(propertiesDeclaration.getAttribute(PropertiesHandler.PROPERTY_SCOPE_PROPERTY));
        if (scope != null)
            PropertiesHandler.addProperty(implementationDescription, CST.A_SCOPE, scope);

        /*
         * Iterate over user defined properties
         */
        for (Element propertyDeclaration : ImplementationHandler.optional(propertiesDeclaration.getElements(
                PropertiesHandler.PROPERTY_DECLARATION, ImplementationHandler.APAM_NAMESPACE))) {

            String name = propertyDeclaration.getAttribute(PropertiesHandler.PROPERTY_NAME_PROPERTY);
            String type = propertyDeclaration.getAttribute(PropertiesHandler.PROPERTY_TYPE_PROPERTY);
            String value = propertyDeclaration.getAttribute(PropertiesHandler.PROPERTY_VALUE_PROPERTY);

            if ((name == null) || (value == null))
                throw new ConfigurationException("APAM properties "
                        + implementationName + ": "
                        + "a property declaration must specifiy a name and value");

            if (type == null)
                type = String.class.getName();

            PropertiesHandler.addProperty(implementationDescription, name, value, type);
        }

    }

    /**
     * APAM properties describe the implementation, they do not apply to instances, so we do not configure them.
     * 
     * @see org.apache.felix.ipojo.Handler#configure(org.apache.felix.ipojo.metadata.Element, java.util.Dictionary)
     */
    @SuppressWarnings("rawtypes")
    @Override
    public void configure(Element componentMetadata, Dictionary configuration) throws ConfigurationException {
    }

    @Override
    public String toString() {
        return "APPAM properties manager for " + getInstanceManager().getInstanceName();
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

}