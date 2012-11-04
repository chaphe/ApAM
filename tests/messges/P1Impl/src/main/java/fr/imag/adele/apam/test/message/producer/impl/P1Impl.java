package fr.imag.adele.apam.test.message.producer.impl;

import fr.imag.adele.apam.message.Message;
import fr.imag.adele.apam.test.message.M1;

public class P1Impl {
    Thread t;
    boolean running=true;
    
    public Message<M1> produceM1() {
        double a =Math.random();
        double b = Math.random();
        Message m = new Message(new M1(a, b));
        m.getProperties().setProperty("vendor", "mehdi");
        return m ;
    }

    public void start() {
        System.out.println("Producer started");
        t = new Thread(new Runnable() {
            @Override
            public void run() {
      
                while (running) {
                    Message<M1> m1 = produceM1();
                    System.out.println(m1.getProperties().getProperty("vendor") + " produce message M1 : " + m1.getData().getMoy() );
                    try {
                        Thread.currentThread().sleep(2000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        });
        t.start();
    }

    public void stop() {
        running = false;
       System.out.println("Producer stopped");
    }
}
