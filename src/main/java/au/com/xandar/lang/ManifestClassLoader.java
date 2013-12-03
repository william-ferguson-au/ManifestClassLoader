/*
 * Copyright (c) Xandar IP 2013.
 * All Rights Reserved
 * No part of this application may be reproduced, copied, modified or adapted, without the prior written consent
 * of the author, unless otherwise indicated for stand-alone materials.
 *
 * Contact support@xandar.com.au for copyright requests.
 */

package au.com.xandar.lang;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * ClassLoader that uses the Manifest Class-Path in an archive in order to determine the classpath.
 * Can handle nested Jar files.
 *
 * Modified from http://qdolan.blogspot.com.au/2008/10/embedded-jar-classloader-in-under-100.html
 */
public final class ManifestClassLoader extends URLClassLoader {

    /**
     * Creates an instance of this ClassLoader and uses it to invoke the supplied class.
     *
     * @param className     Fully qualified name of the class to invoke.
     * @param args          Arguments to supplied to the class's main method.
     */
    public static final void invokeMain(String className, String[] args) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        final ManifestClassLoader classLoader = new ManifestClassLoader();
        final Class mainClass = classLoader.loadClass(className);
        final Method mainMethod = mainClass.getMethod("main", new Class[] { String[].class });
        mainMethod.invoke(null, new Object[] { args });
    }


    /**
     * Constructs this ClassLoader from the SystemClassLoader URLs and the ContextClassLoader.
     */
    public ManifestClassLoader() {
        this(getSystemClassLoaderURLs(), Thread.currentThread().getContextClassLoader());
    }

    public ManifestClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        try {
            final ProtectionDomain protectionDomain = getClass().getProtectionDomain();
            final CodeSource codeSource = protectionDomain.getCodeSource();
            final URL rootJarUrl = codeSource.getLocation();
            final String rootJarName = rootJarUrl.getFile();
            if (isJar(rootJarName)) {
                addResource(new File(rootJarUrl.getPath()));
                addResource(new File(rootJarUrl.getPath(), "!WEB-INF/classes"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            Class<?> clazz = findLoadedClass(name);
            if (clazz == null) {
                clazz = findClass(name);
                if (resolve) {
                    resolveClass(clazz);
                }
            }
            return clazz;
        } catch (ClassNotFoundException e) {
            return super.loadClass(name, resolve);
        }
    }

    private void addResource(File file) throws IOException {
        System.out.println("AddResource: "  + file);
        addURL(file.toURI().toURL());
        if (!isJar(file.getName())) {
            return;
        }

        final JarFile jarFile = new JarFile(file);
        final Enumeration<JarEntry> jarEntries = jarFile.entries();
        while (jarEntries.hasMoreElements()) {
            final JarEntry jarEntry = jarEntries.nextElement();
            if (!jarEntry.isDirectory() && isJar(jarEntry.getName())) {
                addResource(jarEntryAsFile(jarFile, jarEntry));
            }
        }
    }

    private static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean isJar(String fileName) {
        final String fileNameStr = fileName.toLowerCase();
        return fileName != null && (fileNameStr.endsWith(".jar") || fileNameStr.endsWith(".war"));
    }

    private static File jarEntryAsFile(JarFile jarFile, JarEntry jarEntry) throws IOException {
        InputStream input = null;
        OutputStream output = null;
        try {
            final String name = jarEntry.getName().replace('/', '_');
            final int i = name.lastIndexOf(".");
            final String extension = i > -1 ? name.substring(i) : "";
            final File file = File.createTempFile(name.substring(0, name.length() - extension.length()) + ".", extension);
            file.deleteOnExit();
            input = jarFile.getInputStream(jarEntry);
            output = new FileOutputStream(file);
            int readCount;
            final byte[] buffer = new byte[4096];
            while ((readCount = input.read(buffer)) != -1) {
                output.write(buffer, 0, readCount);
            }
            return file;
        } finally {
            close(input);
            close(output);
        }
    }

    private static URL[] getSystemClassLoaderURLs() {
        return ((URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs();
    }
}
