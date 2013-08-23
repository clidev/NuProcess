package org.nuprocess.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Brett Wooldridge
 */
public abstract class BaseEventProcessor<T extends BasePosixProcess> implements IEventProcessor<T>
{
    protected static final int EVENT_BATCH_SIZE;
    protected static final int DEADPOOL_POLL_INTERVAL;
    protected static final int LINGER_ITERATIONS;

    protected Map<Integer, T> pidToProcessMap;
    protected Map<Integer, T> fildesToProcessMap;

    private CyclicBarrier startBarrier;
    private AtomicBoolean isRunning;

    static
    {
        EVENT_BATCH_SIZE = Integer.getInteger("org.nuprocess.eventBatchSize", 1); 

        int lingerTimeMs = Math.max(1000, Integer.getInteger("org.nuprocess.lingerTimeMs", 2500));

        DEADPOOL_POLL_INTERVAL = Math.min(lingerTimeMs, Math.max(100, Integer.getInteger("org.nuprocess.deadPoolPollMs", 250)));
        
        LINGER_ITERATIONS = lingerTimeMs / DEADPOOL_POLL_INTERVAL;
    }

    public BaseEventProcessor()
    {
        pidToProcessMap = new ConcurrentHashMap<Integer, T>();
        fildesToProcessMap = new ConcurrentHashMap<Integer, T>();
        isRunning = new AtomicBoolean();
    }

    /**
     * The primary run loop of the kqueue event processor.
     */
    @Override
    public void run()
    {
        try
        {
            startBarrier.await();

            int idleCount = 0;
            do
            {
                if (process())
                {
                    idleCount = 0;
                }
                else
                {
                    idleCount++;
                }
            }
            while (!isRunning.compareAndSet(pidToProcessMap.isEmpty() && idleCount > LINGER_ITERATIONS, false));
            isRunning.set(false);
        }
        catch (Exception e)
        {
            // TODO: how to handle this error?
            isRunning.set(false);
        }
    }


    /**
     * Get the CyclicBarrier that this thread should join, along with the OsxProcess
     * thread that is starting this processor.  Used to cause the OsxProcess to wait
     * until the processor is up and running before returning from start() to the
     * user.
     *
     * @param processorRunning a CyclicBarrier to join
     */
    @Override
    public CyclicBarrier getSpawnBarrier()
    {
        startBarrier = new CyclicBarrier(2);
        return startBarrier;
    }

    /**
     * @return
     */
    @Override
    public boolean checkAndSetRunning()
    {
        return isRunning.compareAndSet(false, true);
    }
}
