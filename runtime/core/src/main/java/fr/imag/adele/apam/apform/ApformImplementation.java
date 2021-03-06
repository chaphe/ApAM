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
package fr.imag.adele.apam.apform;

import java.util.Map;

import fr.imag.adele.apam.declarations.ImplementationDeclaration;
import fr.imag.adele.apam.impl.ComponentImpl;

public interface ApformImplementation extends ApformComponent {
	
	/**
	 * Get the development model associated with the the implementation
	 */
	public ImplementationDeclaration getDeclaration();
	
    /**
     * Creates an instance of that implementation, and initialize its properties with the set of provided properties.
     * <p>
     * 
     * @param initialproperties the initial properties
     * @return the platform instance
     */
    public ApformInstance createInstance(Map<String, String> initialproperties) throws ComponentImpl.InvalidConfiguration;

    /**
     * If a specification exists in the platform, returns the associated spec.
     * 
     * @return
     */
    public ApformSpecification getSpecification(); // If existing. In general returns null !!

}
