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
package fr.imag.adele.apam.app2.main.impl;

import fr.imag.adele.apam.app2.main.spec.App2MainSpec;
import fr.imag.adele.apam.app2.spec.App2Spec;

public class MainApp2 implements App2Spec, App2MainSpec {

    @Override
    public void call(String texte) {
        texte = texte + " >>> " + MainApp2.class.getSimpleName();
        System.out.println(texte + " # {End Of the Call");
    }

    public void callDep(String texte) {
        System.out.println("--- Calling DepApp2 from MainApp2 ---");
        texte = texte + " >>> " + MainApp2.class.getSimpleName();
        System.out.println(texte + " # {End Of the Call");
    }

}
