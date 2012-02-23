package org.apache.s4.example.edsl.counter;

import java.util.concurrent.TimeUnit;

import org.apache.s4.base.Event;
import org.apache.s4.edsl.BuilderS4DSL;

import com.google.inject.Guice;
import com.google.inject.Injector;

/**
 * This is a sample application to test the S4 embedded domain-specific language (EDSL).
 * 
 * <p>
 * Grammar:
 * 
 * <pre>
 *  (pe , type , prop* , (fireOn , afterInterval? , afterNumEvents?)? , (timer, withPeriod)? , 
 *  (cache, size , expires? )? , asSingleton? , (emit, onField?, 
 *  (withKey|withKeyFinder)?, to )*  )+ , build
 * </pre>
 * 
 * <p>
 * See the <a href="http://code.google.com/p/diezel">Diezel</a> project for details.
 * 
 */
final public class CounterApp extends BuilderS4DSL {

    public static void main(String[] args) {
        Injector injector = Guice.createInjector(new Module());
        CounterApp myApp = injector.getInstance(CounterApp.class);

        /* Normally. the container will handle this but this is just a test. */
        myApp.init();
        myApp.start();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        myApp.close();
    }

    @Override
    protected void onInit() {

        pe("Print").type(PrintPE.class).asSingleton().

        pe("User Count").type(CounterPE.class).fireOn(Event.class).afterInterval(100, TimeUnit.MILLISECONDS)
                .emit(CountEvent.class).withKeyFinder(CountKeyFinder.class).to("Print").

                pe("Gender Count").type(CounterPE.class).fireOn(Event.class).afterInterval(100, TimeUnit.MILLISECONDS)
                .emit(CountEvent.class).withKeyFinder(CountKeyFinder.class).to("Print").

                pe("Age Count").type(CounterPE.class).fireOn(Event.class).afterInterval(100, TimeUnit.MILLISECONDS)
                .emit(CountEvent.class).withKeyFinder(CountKeyFinder.class).to("Print").

                pe("Generate User Event").type(GenerateUserEventPE.class).timer().withPeriod(1, TimeUnit.MILLISECONDS)
                .asSingleton().

                emit(UserEvent.class).withKeyFinder(UserIDKeyFinder.class).to("User Count").

                emit(UserEvent.class).withKey("gender").to("Gender Count").

                emit(UserEvent.class).withKeyFinder(AgeKeyFinder.class).to("Age Count").

                build();
    }

    /*
     * Create and send 200 dummy events of type UserEvent.
     * 
     * @see io.s4.App#start()
     */
    @Override
    protected void onStart() {

    }

    @Override
    protected void onClose() {
        System.out.println("Bye.");
    }

    // Make hooks public for testing. Normally this is handled by the container.
    public void init() {
        super.init();
    }

    public void start() {
        super.start();
    }

    public void close() {
        super.close();
    }

}
