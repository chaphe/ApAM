package fr.imag.adele.apam.test.s3Impl;

import fr.imag.adele.apam.test.s3.S3_1;
import fr.imag.adele.apam.test.s3.S3_2;
import fr.imag.adele.apam.test.s4.S4;
import fr.imag.adele.apam.test.s5.S5;

public class S3Impl implements S3_1, S3_2 {

    S5 s5;
    S4 s4;

    @Override
    public void callS3_1(String s) {
        System.out.println("S3_1 called " + s);
        s4.callS4("from S1Impl");
    }

    @Override
    public void callS3_2(String s) {
        System.out.println("S3_2 called " + s);
    }

    @Override
    public void callS3_1toS5(String msg) {
        System.out.println("S3_1 to S5 called : " + msg);
        s5.callS5("from S3_1toS5 " + msg);
    }
}
