/*
 * Copyright 2015 Thomas Frühbeck, fruehbeck(at)aon(dot)at.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package at.tfr.sec;

import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 *
 * @author Thomas Frühbeck
 */
public class TestNashornSecurityWithWildflySecurityManager {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private ScriptEngineManager scriptEngineManager;
    private List<ScriptEngineFactory> engineFactories;
    private NashornScriptEngineFactory engineFactory;

    final String scriptCheckFileAccess = "\n"
            + "var Files = Java.type('java.nio.file.Files'); \n"
            + "var tmp = Files.createTempDirectory('script'); \n"
            + "print('created TempDir: '+tmp); \n"
            + "var file = Files.createFile(tmp.resolve('veryBadFile.txt')); \n"
            + "Files.write(file, 'very bad text'.getBytes()); \n"
            + "print('I could write: '+Files.readAllLines(file)+' from file:'+file); \n";

    @Test
    public void testNoClassFilter() throws Exception {

        // Given: ScriptEngine w/o ClassFilter:
        final ScriptEngine engine = engineFactory.getScriptEngine();

        // When: call script with Java.type(String) access
        engine.eval(scriptCheckFileAccess);

        // Then no Exception
    }

    @Test
    public void testSecurityManager() throws Exception {

        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectCause(CoreMatchers.isA(Exception.class));

        // Given: read-all-java.policy
        URL url = this.getClass().getResource("/read-all-java.policy");
        System.setProperty("java.securiy.policy", url.toString());
        System.setProperty("java.security.debug", "all");

        // Given: ScriptEngine with ClassFilter
        final ScriptEngine engine = engineFactory.getScriptEngine();
        // Given: WildflySecurityManager enabled
        System.setSecurityManager(new WildFlySecurityManager());


        // When: call script with Java.type(String) access
        engine.eval(scriptCheckFileAccess);

        // Then RuntimeException - ClassNotFoundException
    }

    @Before
    public void setUp() {
        scriptEngineManager = new ScriptEngineManager();
        engineFactories = scriptEngineManager.getEngineFactories();
        engineFactory = (NashornScriptEngineFactory) engineFactories.stream()
                .filter(f -> f.getNames().contains("Nashorn")).findFirst().orElse(null);
    }
}
