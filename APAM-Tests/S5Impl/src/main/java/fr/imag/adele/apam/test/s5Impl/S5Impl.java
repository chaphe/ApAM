package fr.imag.adele.apam.test.s5Impl;

import fr.imag.adele.apam.Instance;
import fr.imag.adele.apam.ApamComponent;
import fr.imag.adele.apam.apamImpl.CST;
import fr.imag.adele.apam.test.s2.S2;
import fr.imag.adele.apam.test.s5.S5;

public class S5Impl implements S5, ApamComponent {
    S2 s2_inv;

    @Override
    public void callS5(String s) {
        System.out.println("S5 called " + s);
        if (s2_inv != null)
            s2_inv.callBackS2(" back to S2 from s5");
        else
            System.err.println("s2_inv is null");
    }

    @Override
    public void apamStart(Instance apamInstance) {
        apamInstance.getComposite().getCompType().setProperty(CST.A_INTERNALINST, CST.V_TRUE);
        apamInstance.getComposite().getCompType().setProperty(CST.A_INTERNALIMPL, CST.V_TRUE);
        System.out.println("set INTERNAL of S5CompEx to true.");
    }

    @Override
    public void apamStop() {
        // TODO Auto-generated method stub

    }

    @Override
    public void apamRelease() {
        // TODO Auto-generated method stub

    }

}