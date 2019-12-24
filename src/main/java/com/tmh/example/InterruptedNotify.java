package com.tmh.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class InterruptedNotify {

    public static void main(String[] args) throws InterruptedException {


         Thread electThread =  new Thread(new Runnable(){
         
             @Override
             public void run() {
                final CountDownLatch latch = new CountDownLatch(3);
                List<Thread> list = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    Thread t = new Thread(new Runnable() {
        
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(new Random().nextInt(5000));
                                latch.countDown();
                            } catch (InterruptedException e) {
                            }
                        }
                    });
                    list.add(t);
                }
                for (Thread thread : list) {
                    thread.start();
                }
                try {
                    latch.await(new Random().nextInt(5000), TimeUnit.MILLISECONDS);
                    if(latch.getCount() == 0){
                        System.out.println("i am new leader");
                    }else{
                        System.out.println("election timeout");
                    }
                } catch (InterruptedException e) {
                   System.out.println("found new leader");
                }
        
             }
         });

         electThread.start();
         Thread.sleep(new Random().nextInt(5000));
         // new leader append
         electThread.interrupt();
         
    }
}