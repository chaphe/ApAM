package fr.imag.adele.apam.test.s2Impl;

import fr.imag.adele.apam.test.s2.S2;
import fr.imag.adele.apam.test.s3.S3_1;
import fr.imag.adele.apam.test.s4.S4;

public class S2Simple implements S2 {

    S3_1 s3;
    S4   s4;

    @Override
    public void callS2(String s) {
        System.out.println("S2 simple called :" + s);
        s3.callS3_1toS5("from S2Simple to S3_1toS5 ");
        s3.callS3_1("from S2Simple to S3_1 ");
        s4.callS4_final("from S2Simple ");
    }

    @Override
    public void callBackS2(String s) {
        // TODO Auto-generated method stub

    }
}