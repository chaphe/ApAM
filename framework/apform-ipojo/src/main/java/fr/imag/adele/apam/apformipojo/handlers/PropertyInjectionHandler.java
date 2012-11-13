package fr.imag.adele.apam.apformipojo.handlers;

import java.util.Dictionary;

import org.apache.felix.ipojo.ConfigurationException;
import org.apache.felix.ipojo.FieldInterceptor;
import org.apache.felix.ipojo.architecture.ComponentTypeDescription;
import org.apache.felix.ipojo.architecture.HandlerDescription;
import org.apache.felix.ipojo.metadata.Attribute;
import org.apache.felix.ipojo.metadata.Element;
import org.apache.felix.ipojo.parser.FieldMetadata;
import org.apache.felix.ipojo.parser.MethodMetadata;
import org.apache.felix.ipojo.util.Callback;

import fr.imag.adele.apam.apformipojo.ApformIpojoComponent;
import fr.imag.adele.apam.apformipojo.ApformIpojoImplementation;
import fr.imag.adele.apam.apformipojo.ApformIpojoInstance;
import fr.imag.adele.apam.core.AtomicImplementationDeclaration;
import fr.imag.adele.apam.core.ImplementationDeclaration;
import fr.imag.adele.apam.core.PropertyDefinition;
import fr.imag.adele.apam.impl.InstanceImpl;

public class PropertyInjectionHandler extends ApformHandler implements FieldInterceptor {

	/**
	 * The registered name of this iPojo handler
	 */
	public static final String NAME = "properties";

	
	@Override
	public void initializeComponentFactory(ComponentTypeDescription typeDesc, Element metadata) throws ConfigurationException {
		
    	if (!(getFactory() instanceof ApformIpojoImplementation))
    		return;

    	ApformIpojoImplementation implementation	= (ApformIpojoImplementation) getFactory();
    	ImplementationDeclaration declaration		= implementation.getDeclaration();
    	
    	if (! (declaration instanceof AtomicImplementationDeclaration))
    		return;
    	
    	AtomicImplementationDeclaration primitive	= (AtomicImplementationDeclaration) declaration;
    	for (PropertyDefinition definition : primitive.getPropertyDefinitions()) {
    		
    		if (definition.getField() != null) {
       			FieldMetadata field	= getPojoMetadata().getField(definition.getField(),String.class.getName());
    			if (field == null)
    				throw new ConfigurationException("Invalid property definition "+definition.getName()+": the specified field does not exist");
    			
    		}
    		
    		if (definition.getCallback() != null) {
      			MethodMetadata method	= getPojoMetadata().getMethod(definition.getName(), new String[] {String.class.getName()});
    			if (method == null)
    				throw new ConfigurationException("Invalid property definition "+definition.getName()+": the specified method does not exist");
    		}
    	}	
    }

	@Override
	public void configure(Element metadata, @SuppressWarnings("rawtypes") Dictionary configuration)	throws ConfigurationException {
        /*
         * Add interceptors to delegate property injection
         * 
         * NOTE All validations were already performed when validating the
         * factory @see initializeComponentFactory, including initializing
         * unspecified properties with appropriate default values. Here we just
         * assume metadata is correct.
         */

    	if (!(getFactory() instanceof ApformIpojoImplementation))
    		return;

    	ApformIpojoImplementation implementation	= (ApformIpojoImplementation) getFactory();
    	ImplementationDeclaration declaration		= implementation.getDeclaration();
    	
    	if (! (declaration instanceof AtomicImplementationDeclaration))
    		return;
    	
    	AtomicImplementationDeclaration primitive	= (AtomicImplementationDeclaration) declaration;
    	for (PropertyDefinition definition : primitive.getPropertyDefinitions()) {
    		
    		if (definition.getField() != null) {
       			FieldMetadata field	= getPojoMetadata().getField(definition.getField());
    	   		getInstanceManager().register(field, this);
    		}
    		
    		if (definition.getCallback() != null) {
      			MethodMetadata method	= getPojoMetadata().getMethod(definition.getName(), new String[] {String.class.getName()});
    			getInstanceManager().addCallback(definition.getName(),new Callback(method,getInstanceManager()));
    		}
    	}
    }
	
	@Override
	public Object onGet(Object pojo, String fieldName, Object value) {
		
		if (getInstanceManager().getApamInstance() == null) {
            System.err.println("property access failure for  " + getInstanceManager().getInstanceName() + " : ASM instance unkown");
            return value;
		}
		
    	if (!(getFactory() instanceof ApformIpojoImplementation))
    		return value;

    	ApformIpojoImplementation implementation	= (ApformIpojoImplementation) getFactory();
    	ImplementationDeclaration declaration		= implementation.getDeclaration();
    	
    	if (! (declaration instanceof AtomicImplementationDeclaration))
    		return value;
    	
    	AtomicImplementationDeclaration primitive	= (AtomicImplementationDeclaration) declaration;
    	for (PropertyDefinition definition : primitive.getPropertyDefinitions()) {
    		
    		if (definition.getField() != null && definition.getField().equals(fieldName)) {
    			getInstanceManager().getApamInstance().getProperty(definition.getName());
    		}
    		
    	}
    	
    	return value;
	}
	
	@Override
	public void onSet(Object pojo, String fieldName, Object value) {
		
		if (getInstanceManager().getApamInstance() == null) {
            System.err.println("property access failure for  " + getInstanceManager().getInstanceName() + " : ASM instance unkown");
            return;
		}
		
    	if (!(getFactory() instanceof ApformIpojoImplementation))
    		return;

    	ApformIpojoImplementation implementation	= (ApformIpojoImplementation) getFactory();
    	ImplementationDeclaration declaration		= implementation.getDeclaration();
    	
    	if (! (declaration instanceof AtomicImplementationDeclaration))
    		return;
    	
    	AtomicImplementationDeclaration primitive	= (AtomicImplementationDeclaration) declaration;
    	for (PropertyDefinition definition : primitive.getPropertyDefinitions()) {
    		
    		if (definition.getField() != null && definition.getField().equals(fieldName)) {
    			((InstanceImpl)getInstanceManager().getApamInstance()).setPropertyInt(definition.getName(),(String) value, true);
    		}
    		
    	}
    	
    	return;
		
	}
	
    /**
     * The description of this handler instance
     * 
     */
    private static class Description extends HandlerDescription {

        private final PropertyInjectionHandler propertyHandler;

        public Description(PropertyInjectionHandler propertyHandler) {
            super(propertyHandler);
            this.propertyHandler = propertyHandler;
        }

        @Override
        public Element getHandlerInfo() {
            Element root = super.getHandlerInfo();

            if (propertyHandler.getInstanceManager() instanceof ApformIpojoInstance) {
                ApformIpojoInstance instance = (ApformIpojoInstance) propertyHandler.getInstanceManager();
                for (PropertyDefinition definition : instance.getFactory().getDeclaration().getPropertyDefinitions()) {
                	
                	/*
                	 * Ignore non injected properties
                	 */
                	if (definition.getField() == null && definition.getCallback() == null)
                		continue;
                	
                	String name		= definition.getName();
                	String field 	= definition.getField();
                	String method	= definition.getCallback();
                	String value	= instance.getApamInstance() != null ? instance.getApamInstance().getProperty(name) :  null;
                	
                	Element property = new Element("property", ApformIpojoComponent.APAM_NAMESPACE);
                	property.addAttribute(new Attribute("name", ApformIpojoComponent.APAM_NAMESPACE, name));
                	property.addAttribute(new Attribute("field", ApformIpojoComponent.APAM_NAMESPACE, field != null ? field : ""));
                	property.addAttribute(new Attribute("method", ApformIpojoComponent.APAM_NAMESPACE, method != null ? method : ""));
                	property.addAttribute(new Attribute("value", ApformIpojoComponent.APAM_NAMESPACE, value != null ? value : ""));

                	root.addElement(property);
                }
            }
            return root;
        }

    }

    @Override
    public HandlerDescription getDescription() {
        return new Description(this);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public String toString() {
        return "APAM property manager for "
                + getInstanceManager().getInstanceName();
    }
}