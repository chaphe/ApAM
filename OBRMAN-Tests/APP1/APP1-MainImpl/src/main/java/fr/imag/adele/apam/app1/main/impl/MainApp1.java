package fr.imag.adele.apam.app1.main.impl;

import fr.imag.adele.apam.app1.s1.spec.S1;
import fr.imag.adele.apam.app1.s2.spec.S2;
import fr.imag.adele.apam.app1.spec.App1Spec;
import fr.imag.adele.apam.app2.spec.App2Spec;

public class MainApp1 implements App1Spec {

    S1       s1;

    S2       s2;

    App2Spec app2;

    private void callS1(String texte) {
        System.out.println("--- Calling S1 from APP1 ---");
        s1.call(texte);
    }

    private void callS2(String texte) {
        System.out.println("--- Calling S2 from APP1 ---");
        s2.call(texte);
    }

    private void callApp2(String texte) {
        System.out.println("--- Calling APP2 from APP1 ---");
        app2.call(texte);
    }

    @Override
    public void call(String texte) {
        texte += " >>> " + MainApp1.class.getSimpleName();
        System.out.println(texte);
        callS1(texte);
        callS2(texte);
        callApp2(texte);

    }
}
