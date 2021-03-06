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
package fr.imag.adele.apam.test.message.consumer;

import java.util.Queue;

import fr.imag.adele.apam.test.message.M1;
import fr.imag.adele.apam.test.message.M3;

public class M13ConsumerPull {


    Queue<M1> queueM1;
    
    Queue<M3> queueM3;

    public void start() {
        System.out.println("Consumer M13 started");
    }
    
    public  Queue<M1> getQueueM1() {
       return queueM1;
    }

    public  Queue<M3> getQueueM3() {
        return queueM3;
     }
    
    public void stop() {
        System.out.println("Consumer M13 stopped");
    }
}
