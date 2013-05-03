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

import org.osgi.framework.Bundle;

import fr.imag.adele.apam.Component;
import fr.imag.adele.apam.declarations.ComponentDeclaration;

public interface ApformComponent {

	public ComponentDeclaration getDeclaration () ;

	public void setProperty(String attr, String value);
	
	   /**
	 * provide the destination real address for the provided relation. Usually
	 * performed as the return of method newWire (when lazy)
	 * 
	 * @param relation
	 *            Name of the relation (field name)
	 * @param destInst
	 *            . Real address of the destination.
	 * @return False if it cannot be performed : legacy.
	 */
    public boolean setLink(Component destInst, String depName);

    /**
	 * Remove a wire. That relation is no longer valid (disappear or other
	 * reason)
	 * 
	 * @param relation
	 *            name of that relation
	 * @param destInst
	 *            the old destination object (if multiple).
	 * @return false if it could not be performed: legacy.
	 */
    public boolean remLink(Component destInst, String depName);

	
    /**
     * Get the bundle in which is located this component.
     */
    public abstract Bundle getBundle();
}
