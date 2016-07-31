/*
 * Copyright 2015 Thomas Frühbeck, fruehbeck(at)aon(dot)at.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package at.tfr.sec;

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

/**
 *
 * @author Thomas Frühbeck
 */
public class TestNashornSecurity {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private ScriptEngineManager scriptEngineManager;
    private List<ScriptEngineFactory> engineFactories;
    private NashornScriptEngineFactory engineFactory;
    final AtomicBoolean wasAskedForJavaLang = new AtomicBoolean();
    final AtomicBoolean wasAskedForJavaIO = new AtomicBoolean();

    final String scriptCheckStringAccess = ""
                + "var str = Java.type('java.lang.String'); "
                + "var s = str.valueOf('BLA'); "
                + "print('I was not filtered: '+s);";

    final String scriptCheckHolderAccess = ""
            + "var imports = new JavaImporter(Packages.at.tfr.sec);"
            + "with(imports) {"
            + " var s = new StringHolder('BLA'); "
            + " print('I was not filtered: '+s);"
            + "}";

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
        engine.eval(scriptCheckStringAccess);

        // Then no Exception
    }

    @Test
    public void testClassFilter() throws Exception {

        // Given: ScriptEngine with ClassFilter
        final ScriptEngine engine = engineFactory.getScriptEngine(new ClassFilter() {

            @Override
            public boolean exposeToScripts(String string) {
                if (string.startsWith("java.lang")) {
                    wasAskedForJavaLang.set(true);
                    return false;
                }
                return true;
            }
        });

        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectCause(CoreMatchers.isA(Exception.class));

        // When: call script with Java.type(String) access
        engine.eval(scriptCheckStringAccess);

        // Then RuntimeException - ClassNotFoundException
        Assert.assertTrue("ClassFilter failed on indirect access to java.lang.String", wasAskedForJavaLang.get());
    }

    @Test
    public void testClassFilterImplicitAccessNoFilter() throws Exception {

        // Given: ScriptEngine w/o ClassFilter
        final ScriptEngine engine = engineFactory.getScriptEngine();

        // When: call Script with indirect access to class String:
        engine.eval(scriptCheckHolderAccess);

        // Then: no Exception, script syntactically correct!
    }

    @Test
    public void testClassFilterImplicitAccess() throws Exception {


        // Given: ScriptEngine with ClassFilter
        final ScriptEngine engine = engineFactory.getScriptEngine(new ClassFilter() {

            @Override
            public boolean exposeToScripts(String string) {
                if (string.startsWith("java.lang")) {
                    wasAskedForJavaLang.set(true);
                    return false;
                }
                return true;
            }
        });

        exceptionRule.expect(CoreMatchers.isA(RuntimeException.class));
        exceptionRule.expectCause(CoreMatchers.isA(ClassNotFoundException.class));

        // When: call Script with indirect access to class String:
        engine.eval(scriptCheckHolderAccess);

        // Then: ClassNotFound Exception - because ClassFilter should filter "java.lang" ??
        // Will it??
        Assert.assertTrue("ClassFilter failed on indirect access to java.lang.String", wasAskedForJavaLang.get());
    }

    @Test
    public void testClassFilterFileSystemAccessNoFilter() throws Exception {

        // Given: ScriptEngine w/o ClassFilter
        final ScriptEngine engine = engineFactory.getScriptEngine();

        // When: call Script with indirect access to class String:
        engine.eval(scriptCheckFileAccess);

        // Then: no Exception, script syntactically correct!
    }

    @Test
    public void testClassFilterFileSystemAccessForJavaNio() throws Exception {


        // Given: ScriptEngine with ClassFilter
        final ScriptEngine engine = engineFactory.getScriptEngine(new ClassFilter() {

            @Override
            public boolean exposeToScripts(String string) {
                if (string.startsWith("java.nio")) {
                    wasAskedForJavaIO.set(true); // just to make sure, Filter was called!!
                }
                return true;
            }
        });

        // When: call Script with indirect access to class String:
        engine.eval(scriptCheckFileAccess);

        // Then: ClassFilter was called and could have checked access
        Assert.assertTrue("ClassFilter had no chance to filter for java.io.File", wasAskedForJavaIO.get());
    }

    @Test
    public void testClassFilterFileSystemAccess() throws Exception {


        // Given: ScriptEngine with ClassFilter
        final ScriptEngine engine = engineFactory.getScriptEngine(new ClassFilter() {

            @Override
            public boolean exposeToScripts(String string) {
                if (string.startsWith("java.io")) {
                    wasAskedForJavaIO.set(true);
                    return false;
                }
                return true;
            }
        });

        exceptionRule.expect(CoreMatchers.isA(RuntimeException.class));
        exceptionRule.expectCause(CoreMatchers.isA(ClassNotFoundException.class));

        // When: call Script with indirect access to class String:
        engine.eval(scriptCheckFileAccess);

        // Then: ClassNotFound Exception - because ClassFilter should filter "java.io" ??
        // Will it??
        Assert.assertTrue("ClassFilter failed on indirect access to java.io.File", wasAskedForJavaIO.get());
    }

    @Before
    public void setUp() {
        scriptEngineManager = new ScriptEngineManager();
        engineFactories = scriptEngineManager.getEngineFactories();
        engineFactory = (NashornScriptEngineFactory)engineFactories.stream()
                        .filter(f->f.getNames().contains("Nashorn")).findFirst().orElse(null);
    }
}
