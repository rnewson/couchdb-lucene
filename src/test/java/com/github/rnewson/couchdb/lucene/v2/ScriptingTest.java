package com.github.rnewson.couchdb.lucene.v2;

import static org.junit.Assert.fail;

import java.io.File;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;

import javax.script.Compilable;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ScriptingTest {

    private static AccessControlContext controlContext;

    private ScriptEngine scriptEngine;
    private Invocable invocable;
    private Compilable compilable;

    @BeforeClass
    public static void createSandbox() throws Exception {
        System.setSecurityManager(new SecurityManager());

        Permissions perms = new Permissions();
        perms.add(new RuntimePermission("accessDeclaredMembers"));

        final CodeSource codeSource = new CodeSource(null, (Certificate[]) null);

        final ProtectionDomain domain = new ProtectionDomain(codeSource, perms);

        controlContext = new AccessControlContext(new ProtectionDomain[] { domain });
    }

    @Before
    public void setup() throws Exception {
        final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
        scriptEngine = scriptEngineManager.getEngineByName("ECMAScript");
        invocable = (Invocable) scriptEngine;
        compilable = (Compilable) scriptEngine;
    }

    @Test
    public void nullReturn() throws Exception {
        final String fun = "function(doc) { return null; }";
        eval(fun);
    }

    @Test
    public void singleDocReturn() throws Exception {
        final String fun = "function(doc) { var ret = new Document();  ret.add('hello'); return ret; }";
        eval(fun);
    }

    @Test
    public void multiDocReturn() throws Exception {
        final String fun = "function(doc) { var ret = []; ret.push(new Document()); ret.push(new Document()); return ret; } ";
        eval(fun);
    }

    @Test(expected = PrivilegedActionException.class)
    public void sandboxEscape() throws Exception {
        final String fun = "function(doc) {return java.io.File.createTempFile(\"tmp\", null);}";
        final File result = (File) eval(fun);
        if (result.exists()) {
            result.delete();
            fail("Created file inside sandbox.");
        }
    }

    private Object eval(final String fun) throws Exception {
        final String fun2 = "importPackage(com.github.rnewson.couchdb.lucene.v2.eval); var obj = new Object(); obj.indexfun="
                + fun;

        scriptEngine.eval(fun2);
        final Object obj = scriptEngine.get("obj");
        final Object result = AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {

            @Override
            public Object run() throws Exception {
                return invocable.invokeMethod(obj, "indexfun", "hi");
            }
        }, controlContext);

        System.out.println(result);
        return result;
    }

}
