/**
 * Copyright 2011-2012 Universite Joseph Fourier, LIG, ADELE team
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package fr.imag.adele.apam.apform.legacy.ipojo;

import java.util.Map;
import java.util.Properties;

import org.apache.felix.ipojo.ComponentInstance;
import org.apache.felix.ipojo.IPojoFactory;
import org.apache.felix.ipojo.architecture.PropertyDescription;
import org.osgi.framework.Bundle;

import fr.imag.adele.apam.apform.ApformImplementation;
import fr.imag.adele.apam.apform.ApformInstance;
import fr.imag.adele.apam.apform.ApformSpecification;
import fr.imag.adele.apam.declarations.ImplementationDeclaration;
import fr.imag.adele.apam.declarations.ImplementationReference;
import fr.imag.adele.apam.declarations.InterfaceReference;
import fr.imag.adele.apam.declarations.PropertyDefinition;

/**
 * This class allow integrating legacy iPojo components in the APAM runtime

 *
 */
public class ApformIPojoImplementation implements ApformImplementation {

	/**
	 * A legacy implementation declaration
	 */
	private static class Declaration extends ImplementationDeclaration {

		protected Declaration(String name) {
			super(name, null);
		}

		@Override
		protected ImplementationReference<?> generateReference() {
			return new Reference(getName());
		}
		
	}
	
	/**
	 * A reference to a legacy declaration
	 *
	 */
	public static class Reference extends ImplementationReference<Declaration> {

		public Reference(String name) {
			super(name);
		}
		
	}
	
	
	/**
	 * The associated iPojo factory
	 */
	private final IPojoFactory factory;
	
	/**
	 * The declaration of this implementation
	 */
	private final ImplementationDeclaration declaration;
	
	public ApformIPojoImplementation(IPojoFactory factory) {
		this.factory 		= factory;
		this.declaration	= new Declaration(factory.getName());
		
		/*
		 * Add the list of provided interfaces
		 */
		for (String providedIntereface : factory.getComponentDescription().getprovidedServiceSpecification()) {
			declaration.getProvidedResources().add(new InterfaceReference(providedIntereface));
		}
		
		/*
		 * Add the list of factory properties
		 */
		for(PropertyDescription  property : factory.getComponentDescription().getProperties()) {
			if (property.isImmutable()) {
				declaration.getPropertyDefinitions().add(
						new PropertyDefinition(declaration, property.getName(), "string", property.getValue(), null, null, true, true));
				declaration.getProperties().put(property.getName(), property.getValue());
			}
		}
	
	}
	
	@Override
	public Bundle getBundle() {
		return factory.getBundleContext().getBundle();
	}
	
	/**
	 * Create a new legacy instance
	 */
	@Override
	public ApformInstance createInstance(Map<String, String> initialProperties) {
		
		try {
			Properties configuration = new Properties();
			if (initialProperties != null)
				configuration.putAll(initialProperties);
			
			ComponentInstance ipojoInstance = factory.createComponentInstance(configuration);
			return new ApformIpojoInstance(ipojoInstance);
			
		} catch (Exception cause) {
			throw new IllegalArgumentException(cause);
		}
	}

	@Override
	public ApformSpecification getSpecification() {
		return null;
	}


	@Override
	public ImplementationDeclaration getDeclaration() {
		return declaration;
	}

	@Override
	public void setProperty(String attr, String value) {
		// TODO see if we can reconfigure factory publication
	}

}