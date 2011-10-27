package org.apache.s4.comm;

import org.apache.s4.base.Emitter;
import org.apache.s4.base.Listener;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import junit.framework.Assert;
import junit.framework.TestCase;

/*
 * Test class to test communication protocols. As comm-layer connections need to be
 *  made including acquiring locks, the test is declared abstract and needs to be 
 *  extended with appropriate protocols.
 * 
 * At a high-level, the test accomplishes the following:
 * <ul>
 * <li> Create Send and Receive Threads </li>
 * <li> SendThread sends out a pre-defined number of messages to all the partitions </li>
 * <li> ReceiveThread receives all/most of these messages </li>
 * <li> To avoid the receiveThread waiting for ever, it spawns a TimerThread that would 
 * interrupt after a pre-defined but long enough interval </li>
 * <li> The receive thread reports on number of messages received and dropped </li>
 * </ul>
 * 
 */
public abstract class SimpleDeliveryTest extends TestCase {
	protected CommWrapper sdt;

	static class CommWrapper {
		final private static int messageCount = 200;
		final private static int timerThreadCount = 100;

		final private Emitter emitter;
		final private Listener listener;
		final private int interval;

		public Thread sendThread, receiveThread;
		private int messagesExpected;
		private int messagesReceived = 0;

		@Inject
		public CommWrapper(@Named("emitter.send.interval") int interval,
				Emitter emitter, Listener listener) {
			this.emitter = emitter;
			this.listener = listener;
			this.interval = interval;
			this.messagesExpected = messageCount
					* this.emitter.getPartitionCount();

			this.sendThread = new SendThread();
			this.receiveThread = new ReceiveThread();
		}

		public boolean moreMessages() {
			return ((messagesExpected - messagesReceived) > 0);
		}

		class SendThread extends Thread {
			@Override
			public void run() {
				try {
					for (int partition = 0; partition < emitter
							.getPartitionCount(); partition++) {
						for (int i = 0; i < messageCount; i++) {
							byte[] message = (new String("message-" + i))
									.getBytes();
							emitter.send(partition, message);
							Thread.sleep(interval);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					return;
				}
			}
		}

		/*
		 * TimerThread - interrupts the passed thread, after specified
		 * time-interval.
		 */
		class TimerThread extends Thread {
			private Thread watchThread;
			private Integer sleepCounter;

			TimerThread(Thread watchThread) {
				this.watchThread = watchThread;
				this.sleepCounter = new Integer(timerThreadCount);
			}

			public void resetSleepCounter() {
				synchronized (this.sleepCounter) {
					this.sleepCounter = timerThreadCount;
				}
			}

			public void clearSleepCounter() {
				synchronized (this.sleepCounter) {
					this.sleepCounter = 0;
				}
			}

			private int getCounter() {
				synchronized (this.sleepCounter) {
					return this.sleepCounter--;
				}
			}

			@Override
			public void run() {
				try {
					while (getCounter() > 0) {
						Thread.sleep(interval);
					}
					watchThread.interrupt();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		class ReceiveThread extends Thread {
			@Override
			public void run() {

				// start the timer thread to interrupt if blocked for too long
				TimerThread timer = new TimerThread(this);
				timer.start();
				while (messagesReceived < messagesExpected) {
					byte[] message = listener.recv();
					timer.resetSleepCounter();
					if (message != null)
						messagesReceived++;
					else
						break;
				}
				timer.clearSleepCounter();
			}
		}
	}

	/**
	 * test() tests the protocol. If all components function without throwing
	 * exceptions, the test passes. The test also reports the loss of messages,
	 * if any.
	 * 
	 * @throws InterruptedException
	 */
	public void test() throws InterruptedException {
		try {
			// start send and receive threads
			sdt.sendThread.start();
			sdt.receiveThread.start();

			// wait for them to finish
			sdt.sendThread.join();
			sdt.receiveThread.join();

			Assert.assertTrue("Guaranteed message delivery",
					!sdt.moreMessages());
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail("The comm protocol has failed basic functionality test");
		}

		Assert.assertTrue("The comm protocol seems to be working crash-free",
				true);

		System.out.println("Done");
	}
}