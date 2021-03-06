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
package fr.imag.adele.apam.util.tracker;

import fr.imag.adele.apam.Instance;
//import fr.imag.adele.apam.util.String;

//import org.osgi.framework.Filter;

/**
 * The {@code InstanceTracker} is a {@code ComponentTracker} specialized in order to track {@code Instance}.
 *
 * User: barjo
 * Date: 05/11/12
 * Time: 11:41
 */
public class InstanceTracker extends ComponentTracker<Instance> {

    public InstanceTracker(final String filter) {
        super(Instance.class, filter);
    }

    public InstanceTracker(final String filter, final ComponentTrackerCustomizer<Instance> customizer) {
        super(Instance.class, filter, customizer);
    }
}