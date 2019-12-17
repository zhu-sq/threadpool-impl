package steap1;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadPoolExecutor {

    //工作线程数组
    private Worker[] workers;

    //任务阻塞队列,是线程安全的，里面每个操作都会加锁处理
    private BlockingQueue<Task> queue;

    // 当前工作线程的数量
    private int workerSize = 0;

    //线程池最大的工作线程数量
    private int poolSize;

    public ThreadPoolExecutor(int poolSize, BlockingQueue<Task> queue) {
        this.poolSize = poolSize;
        this.workers = new Worker[poolSize];
        this.queue = queue;
    }


    public void execute(Task task) {
        //如果线程池的线程数量小于最大值，则添加线程
        //否则将任务放入队列中
        if (workerSize < poolSize) {
            addWorker(task);
        } else {
            this.queue.add(task);
        }
    }

    //添加worker工作线程，并立即执行
    private synchronized void addWorker(Task task) {
        //这里做个双重判定，判定线程数量是否小于最大值
        if (workerSize >= poolSize) {
            this.queue.add(task);
            return;
        }

        //构建worker，并启动线程
        workers[workerSize] = new Worker(task);
        workers[workerSize].t.start();

        workerSize++;
    }


    //实际运行的代码
    void runWorker(Worker worker){
        Task task =(Task) worker.task;
        try {
            while (true){
                //线程在这个循环中不断的获取任务来执行
                // queue.task() 方法是一个线程安全的阻塞方法
                //如果队列没有任务，那么所有工作线程都会在这里阻塞，等待获取可用的任务
                if(task == null){
                    task = this.queue.take();
                }
                task.run();

                task = null;
            }
        }catch (InterruptedException e){
            e.printStackTrace();
        }

    }

    //工作线程包装类
    private class Worker implements Runnable {
        private Runnable task;

        final Thread t;

        public Worker(Runnable task) {
            this.task = task;
            this.t = new Thread(this);
        }

        @Override
        public void run() {
            runWorker(this);
        }
    }


    //任务类
    static class Task implements Runnable {

        private int tag;

        public Task(int tag) {
            this.tag = tag;
        }

        @Override
        public void run() {
            System.out.printf("任务 %d 开始执行 \n", tag);
            System.out.printf("任务 %d 执行中 \n", tag);
            System.out.printf("任务 %d 执行结束\n", tag);
        }
    }
}
