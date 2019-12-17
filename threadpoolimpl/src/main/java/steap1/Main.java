package steap1;

import java.util.concurrent.LinkedBlockingQueue;

public class Main {

    public static void main(String[] args){
        ThreadPoolExecutor executor = new ThreadPoolExecutor(8,new LinkedBlockingQueue<>());
        for(int i=0;i<1000;i++){
            executor.execute(new ThreadPoolExecutor.Task(i));
        }
    }
}
