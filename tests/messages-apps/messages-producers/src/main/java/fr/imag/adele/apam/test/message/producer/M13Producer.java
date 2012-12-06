package fr.imag.adele.apam.test.message.producer;

import fr.imag.adele.apam.test.message.M1;
import fr.imag.adele.apam.test.message.M3;

public class M13Producer {
    
    
    public M1 produceM1() {
        double a =Math.random();
        double b = Math.random();
        return new M1(a,b) ;
    }

    public M3 produceM3() {
        double a =Math.random();
        double b = Math.random();
        return new M3(a,b) ;
    }

    public void start() {
        System.out.println("M13 Producer started");
    }

    public void stop() {
       System.out.println("M13 Producer stopped");
    }

}