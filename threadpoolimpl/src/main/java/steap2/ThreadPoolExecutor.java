package steap2;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ThreadPoolExecutor {

    //工作线程集合
    private volatile Set<Worker> workers;

    //任务阻塞队列,是线程安全的，里面每个操作都会加锁处理
    private volatile BlockingQueue<Task> queue;

    //线程池最大的工作线程数量
    private volatile int poolSize;

    //线程池的状态
    private volatile int state = STATE_RUNNING;

    //线程池的2种状态
    private final static int STATE_RUNNING = -1;
    private final static int STATE_SHUTDOWN = 0;
    private final static int STATE_SHUTDOWN_NOW = 2;

    //是否有闲置时间的限制
    private volatile boolean allowThreadTimeOut = false;
    //线程最长闲置时间
    private volatile long keepAliveTime;

    //全局锁
    private ReentrantLock mainLock = new ReentrantLock();


    /**
     * 构建一个固定数量的线程池
     *
     * @param poolSize 线程数量
     * @param queue    任务队列
     */
    public ThreadPoolExecutor(int poolSize, BlockingQueue<Task> queue) {
        this.poolSize = poolSize;
        this.workers = new HashSet<>();
        this.queue = queue;
    }

    /**
     * 构建一个有线程数量限制的线程池，而且线程存在最长闲置时间，超时将停止线程
     *
     * @param poolSize 线程数量
     * @param timeout  最长闲置时间
     * @param timeUnit 时间的单位
     * @param queue    任务队列
     */
    public ThreadPoolExecutor(int poolSize, long timeout, TimeUnit timeUnit, BlockingQueue<Task> queue) {
        this.poolSize = poolSize;
        this.workers = new HashSet<>();
        this.queue = queue;
        this.keepAliveTime = timeUnit.toNanos(timeout);
        this.allowThreadTimeOut = true;
    }


    /**
     * 任务提交接口
     *
     * @param task 任务
     */
    public void execute(Task task) {

        if (state > STATE_RUNNING)
            throw new RejectedExecutionException("线程池已关闭，禁止提交任务");

        if (workers.size() < poolSize) {
            addWorker(task);
        } else {
            this.queue.add(task);
        }
    }

    /**
     * 停止线程池
     * 温和的停止，拒绝提交新任务，但是已提交的任务会全部完成
     */
    public void shutdown() {
        this.state = STATE_SHUTDOWN;
    }

    /**
     * 强制停止线程池
     * 拒绝提交任务，剩下的任务不再执行，并尝试中断线程池
     */
    public void shutdownNow() {
          this.state = STATE_SHUTDOWN_NOW;
          for(Worker w:workers){
              try {
                  w.t.interrupt();
              }catch (Exception e){
                  e.printStackTrace();
              }
          }
    }


    /**
     * 添加worker工作线程，并立即执行
     * 这里做个双重判定，防止并发时多创建了线程
     */
    private void addWorker(Task task) {
        mainLock.lock();
        try {
            if (workers.size() >= poolSize) {
                this.queue.add(task);
                return;
            }
            Worker w = new Worker(task);
            workers.add(w);
            w.t.start();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 工作线程实际运行任务的方法
     */
    void runWorker(Worker worker) {
        Task task = (Task) worker.task;
        boolean completedAbruptly = false;
        try {
            while (true) {
                //线程在这个循环中不断的获取任务来执行
                // getTask() 为从队列中获取任务，如果为null，表示该线程超过闲置时间限制，停止该线程
                if (task == null) {
                    task = getTask();
                }

                if (task == null) {
                    completedAbruptly = true;
                    break;
                }

                task.run();
                task = null;
            }
            completedAbruptly = true;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //线程中断，做一些处理
            processWorkerExit(worker, completedAbruptly);
        }
    }

    /**
     * 线程中断，重新开启线程
     *
     * @param w
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        //如果是因为线程池关闭导致线程中断，不做任何处理
        if (completedAbruptly)
            return;

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }

        //判断线程池是否关闭状态
        if (state == STATE_SHUTDOWN)
            return;

        addWorker(null);
    }

    /**
     * 从队列中获取可用的线程
     *
     * @return
     */
    private Task getTask() throws InterruptedException {
        if (state == STATE_SHUTDOWN_NOW)
            return null;

        if (state == STATE_RUNNING && queue.size() <= 0) {
            return null;
        }

        //如果有闲置时间限制，使用poll方法
        //一定时间内还未获得可用任务，返回null
        if (allowThreadTimeOut) {
            return queue.poll(keepAliveTime, TimeUnit.NANOSECONDS);
        }

        return queue.take();
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
