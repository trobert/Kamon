package kamon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

public class IsolatedClassLoader extends URLClassLoader {

        private IsolatedClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (useBootstrapClassLoader(name)) {
                return super.findClass(name);
            }
            String resourceName = name.replace('.', '/') + ".class";
            InputStream input = getResourceAsStream(resourceName);
            if (input == null) {
                throw new ClassNotFoundException(name);
            }
            byte[] b;

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];


            try {
            while ((nRead = input.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            b = buffer.toByteArray();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            int lastIndexOf = name.lastIndexOf('.');
            if (lastIndexOf != -1) {
                String packageName = name.substring(0, lastIndexOf);
                createPackageIfNecessary(packageName);
            }
            return defineClass(name, b, 0, b.length);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (useBootstrapClassLoader(name)) {
                return super.loadClass(name, resolve);
            }
            return findClass(name);
        }

        private boolean useBootstrapClassLoader(String name) {
            return name.startsWith("java.") || name.startsWith("sun.")
                    || name.startsWith("javax.management.")
                    || name.startsWith("kamon.instrumentation");
        }

        private void createPackageIfNecessary(String packageName) {
            if (getPackage(packageName) == null) {
                definePackage(packageName, null, null, null, null, null, null, null);
            }
        }
    }