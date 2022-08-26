/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.core.v1;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Factory;
import org.apache.commons.collections4.FluentIterable;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.appender.db.AbstractDatabaseManager;
import org.apache.logging.log4j.core.appender.db.jdbc.JdbcDatabaseManager;
import org.apache.logging.log4j.core.appender.nosql.NoSqlDatabaseManager;
import org.apache.logging.log4j.core.config.plugins.convert.TypeConverter;
import org.apache.logging.log4j.core.config.plugins.processor.PluginCache;
import org.apache.logging.log4j.core.config.plugins.processor.PluginEntry;
import org.apache.logging.log4j.core.net.MailManager.FactoryData;
import org.apache.logging.log4j.core.net.SmtpManager;
import org.apache.logging.log4j.core.net.SmtpManager.SMTPManagerFactory;
import org.apache.logging.log4j.core.tools.picocli.CommandLine;
import org.apache.logging.log4j.core.util.ReflectionUtil;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.apache.logging.log4j.util.IndexedStringMap;
import org.apache.logging.log4j.util.TriConsumer;
import org.hamcrest.Matcher;
import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.compiler.CompilerUtil;
import org.jd.core.v1.compiler.InMemoryClassLoader;
import org.jd.core.v1.compiler.InMemoryJavaSourceFileObject;
import org.jd.core.v1.loader.ClassPathLoader;
import org.jd.core.v1.loader.ZipLoader;
import org.jd.core.v1.printer.PlainTextPrinter;
import org.jd.core.v1.regex.PatternMaker;
import org.jsoup.nodes.Attribute;
import org.junit.Test;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.Vector;

public class MiscTest extends AbstractJdTest {
    
    @Test
    public void testCreateDirs() throws Exception {
        class CreateDirs {
            @SuppressWarnings("unused")
            void createDirs(Path outputDirectory) throws IOException {
                Files.createDirectories(outputDirectory, new FileAttribute[0]);
            }
        }
        String internalClassName = CreateDirs.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testMapFilter() throws Exception {
        class MapFilter {
            IndexedStringMap map;
            boolean isAnd;
            @SuppressWarnings({ "unused", "unchecked" })
            boolean filter(Map<String, String> data) {
                boolean match = false;
                for (int i = 0; i < map.size(); i++) {
                    final String toMatch = data.get(map.getKeyAt(i));
                    match = toMatch != null
                            && ((List<String>) map.getValueAt(i))
                            .contains(toMatch);
                    if ((!isAnd && match) || (isAnd && !match)) {
                        break;
                    }
                }
                return match;
            }        
        }
        String internalClassName = MapFilter.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testEmptyIfNull() throws Exception {
        class EmptyIfNull<K, V> {
            Map<K, Collection<V>> map;

            @SuppressWarnings("unused")
            Collection<V> remove(final Object key) {
                return CollectionUtils.emptyIfNull(getMap().remove(key));
            }

            Map<K, ? extends Collection<V>> getMap() {
                return map;
            }
        }
        String internalClassName = EmptyIfNull.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testServiceLoader() throws Exception {
        class SvcLoader {
            @SuppressWarnings("unused")
            static <T> Iterable<T> callServiceLoader(MethodHandle handle, Class<T> serviceType, ClassLoader classLoader) throws Throwable {
                ServiceLoader<T> serviceLoader = (ServiceLoader<T>) handle.invokeExact(serviceType, classLoader);
                return serviceLoader;
            }
        }
        String internalClassName = SvcLoader.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testExpectedExceptionMatcherBuilder() throws Exception {
        class ExpectedExceptionMatcherBuilder {
            private final List<Matcher<?>> matchers = new ArrayList<Matcher<?>>();
            @SuppressWarnings({"unchecked", "rawtypes", "unused"})
            private List<Matcher<? extends Throwable>> castedMatchers() {
                return new ArrayList<Matcher<? extends Throwable>>((Collection) matchers);
            }
        }
        String internalClassName = ExpectedExceptionMatcherBuilder.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testMap() throws Exception {
        class MapIter {
            @SuppressWarnings("unused")
            void iter(PluginCache cache) {
                for (Entry<String, Map<String, PluginEntry>> outer : cache.getAllCategories().entrySet()) {
                    for (Entry<String, PluginEntry> inner : Collections.synchronizedSet(outer.getValue().entrySet())) {
                        PluginEntry entry = inner.getValue();
                        System.out.println(entry);
                    }
                }
            }
        }
        String internalClassName = MapIter.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testMap1() throws Exception {
        class MapIter {
            @SuppressWarnings("unused")
            void iter(PluginCache cache) {
                for (Entry<String, Map<String, PluginEntry>> outer : cache.getAllCategories().entrySet()) {
                    for (Entry<String, PluginEntry> inner : Objects.requireNonNull(outer.getValue().entrySet())) {
                        PluginEntry entry = inner.getValue();
                        System.out.println(entry);
                    }
                }
            }
        }
        String internalClassName = MapIter.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testMap2() throws Exception {
        class MapIter2 {
            @SuppressWarnings("unused")
            void iter(Map<String, String> headers) throws Exception {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    System.out.println(header.getKey().getBytes("UTF-8"));
                }
            }
        }
        String internalClassName = MapIter2.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testMap3() throws Exception {
        class MapTest {
            @SuppressWarnings("unused")
            private static TriConsumer<String, String, Map<String, String>> PUT_ALL;
            static {
                PUT_ALL = (key, value, stringStringMap) -> stringStringMap.put(key, value);
            }
        }
        String internalClassName = MapTest.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testMapLambda() throws Exception {
        String internalClassName = MapLambda.class.getName().replace('.', '/');
        try (InputStream is = this.getClass().getResourceAsStream("/jar/map-lambda-jdk8u331.jar")) {
            Loader loader = new ZipLoader(is);
            String source = decompileSuccess(loader, new PlainTextPrinter(), internalClassName);

            // Check decompiled source code
            assertTrue(source.matches(PatternMaker.make("this.map.forEach((key, value) -> result.put(key, value));")));

            // Recompile decompiled source code and check errors
            InMemoryClassLoader classLoader = new InMemoryClassLoader();
            assertTrue(CompilerUtil.compile("1.8", classLoader, new InMemoryJavaSourceFileObject(internalClassName, source)));
            
            Class<?> recompiledClass = classLoader.findClassByInternalName(internalClassName);
            assertNotNull(recompiledClass);
            assertTrue(classLoader.canLoad(internalClassName));
            assertNotNull(classLoader.load(internalClassName));
            
            String eclipseSource = decompileSuccess(classLoader, new PlainTextPrinter(), internalClassName);

            // Check decompiled source code
            assertEquals(source, eclipseSource);
        }
    }
    
    @Test
    public void testIndexedStringMapLambda() throws Exception {
        String internalClassName = IndexedStringMapLambda.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("this.map.forEach((key, value) -> result.put(key, (List)value));")));

        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testUnion() throws Exception {
        class Union {
            @SuppressWarnings("unused")
            void test(Set<Integer> ints, Set<Double> doubles) {
                SetView<Number> union = Sets.union(ints, doubles);
            }
        }
        String internalClassName = Union.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("SetView<Number> union = Sets.union(ints, doubles);")));
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testUnion2() throws Exception {
        class Union2 {
            @SuppressWarnings("unused")
            <V, W extends V, X extends V> void test(Set<W> vs, Set<X> ws) {
                SetView<V> union = Sets.<V>union(vs, ws);
            }
        }
        String internalClassName = Union2.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("SetView<V> union = Sets.union(vs, ws);")));
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testUnion3() throws Exception {
        class Union3 {
            @SuppressWarnings("unused")
            <V> void test(Set<? extends V> vs, Set<? extends V> ws) {
                SetView<V> union = Sets.<V>union(vs, ws);
            }
            @SuppressWarnings("unused")
            <V> void test(Set<? extends V> vs) {
                SetView<V> union = Sets.<V>union(vs, vs);
            }
        }
        String internalClassName = Union3.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("SetView<V> union = Sets.union(vs, ws);")));
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testManagerImpl() throws Exception {
        abstract class ManagerImpl {
            static NoSQLDatabaseManagerFactory FACTORY;

            abstract <X extends AbstractManager, Y> X getManager(ManagerFactory<X, Y> factory);

            @SuppressWarnings("unused")
            NoSqlDatabaseManager<?> getManager() {
                return getManager(FACTORY);
            }

            abstract class NoSQLDatabaseManagerFactory implements ManagerFactory<NoSqlDatabaseManager<?>, FactoryData> {}
        }
        String internalClassName = ManagerImpl.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testManagerImpl2() throws Exception {
        abstract class ManagerImpl2 {
            static SMTPManagerFactory FACTORY;            

            abstract <X extends AbstractManager, Y> X getManager(ManagerFactory<X, Y> factory);
            
            @SuppressWarnings("unused")
            SmtpManager getManager() {
                return (SmtpManager) getManager(FACTORY);
            }
        }
        String internalClassName = ManagerImpl2.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testManager() throws Exception {
        class Manager {
            @SuppressWarnings("unused")
            static <M extends AbstractDatabaseManager, T> M getManager(String name, T data, ManagerFactory<M, T> factory) {
                return AbstractManager.getManager(name, factory, data);
            }
        }
        String internalClassName = Manager.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);

        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("return (M)AbstractManager.getManager(name, factory, data);")));
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testPriviledgedAction() throws Exception {
        class PriviledgedAction {
            @SuppressWarnings("all")
            <T> void doPriviledged() throws PrivilegedActionException {
                AccessController.doPrivileged((PrivilegedAction<T>) () -> null);
            }
        }
        String internalClassName = PriviledgedAction.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);

        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testConsumer() throws Exception {
        class Consumer {
            @SuppressWarnings({ "unused" })
            void accept(LogEvent event, TriConsumer<String, Object, StringBuilder> mapWriter, StringBuilder builder) {
                if (event.getMessage() instanceof MapMessage) {
                    ((MapMessage<?, ?>) event.getMessage()).forEach((key, value) -> mapWriter.accept(key, value, builder));
                }
            }
        }
        String internalClassName = Consumer.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);

        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testIterable() throws Exception {
        class Iter {
            @SuppressWarnings({ "unused", "unchecked" })
            static List<Object> allParameters(Object parameters) throws Throwable {
                if (parameters instanceof Iterable) {
                    List<Object> result = new ArrayList<Object>();
                    for (Object entry : ((Iterable<Object>) parameters)) {
                        result.add(entry);
                    }
                    return result;
                }
                return null;
            }
        }
        String internalClassName = Iter.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);

        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testEnumConverter() throws Exception {
        class EnumConverter {
            @SuppressWarnings("unused")
            CommandLine.ITypeConverter<?> getTypeConverter(Class<?> type) {
                if (type.isEnum()) {
                    return new CommandLine.ITypeConverter<Object>() {
                        @SuppressWarnings({ "unchecked", "rawtypes" })
                        public Object convert(String value) throws Exception {
                            return Enum.valueOf((Class<? extends Enum>) type, value);
                        }
                        
                        @Override
                        @SuppressWarnings({ "unchecked", "rawtypes" })
                        public String toString() {
                            return Enum.valueOf((Class<? extends Enum>) type, "").toString();
                        }
                    };
                }
                return null;
            }
        }
        String internalClassName = EnumConverter.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);

        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testEnumMap() throws Exception {
        try (InputStream is = this.getClass().getResourceAsStream("/jar/enum-map-jdk8u331.jar")) {
            Loader loader = new ZipLoader(is);
            String internalClassName = EnumMapUtil.class.getName().replace('.', '/');
            String source = decompileSuccess(loader, new PlainTextPrinter(), internalClassName);
            
            // Recompile decompiled source code and check errors
            assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
        }
    }
    
    @Test
    public void testNamespacesStack() throws Exception {
        class NamespacesStack {
            Deque<Map<String, String>> namespacesStack = new ArrayDeque<>();

            @SuppressWarnings("unused")
            void test(String prefix, Attribute attr) {
                namespacesStack.peek().put(prefix, attr.getValue());
            }
        }
        String internalClassName = NamespacesStack.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testSafeList1() throws Exception {
        class SafeList1 {
            Map<String, Set<String>> attributes;

            @SuppressWarnings("unused")
            SafeList1() {
                for (Map.Entry<String, Set<String>> copyTagAttributes : attributes.entrySet()) {
                    attributes.put(copyTagAttributes.getKey(), new HashSet<>(copyTagAttributes.getValue()));
                }
            }
        }
        String internalClassName = SafeList1.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("attributes.put(copyTagAttributes.getKey(), new HashSet<>(copyTagAttributes.getValue()));")));

        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testSafeList2() throws Exception {
        class SafeList2 {
            Map<String, Map<String, String>> enforcedAttributes;
            
            @SuppressWarnings("unused")
            void test(String attrKey, String attrVal, String tagName) {
                enforcedAttributes.get(tagName).put(attrKey, attrVal);
            }
        }
        String internalClassName = SafeList2.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("enforcedAttributes.get(tagName).put(attrKey, attrVal);")));

        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testNewInstance() throws Exception {
        class NewInstance {
            @SuppressWarnings("unused")
            NewInstance newCastTests(Class<? extends NewInstance> clazz) throws Exception {
                return newInstanceOf(clazz);
            }

            @SuppressWarnings({ "unchecked", "unused" })
            <T> T newInstanceOf(String className) throws Exception {
                return newInstanceOf((Class<T>) Class.forName(className));
            }

            <T> T newInstanceOf(Class<T> clazz) throws Exception {
                return clazz.getConstructor().newInstance();
            }
        }
        String internalClassName = NewInstance.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);

        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("return newInstanceOf(clazz);")));
        assertTrue(source.matches(PatternMaker.make("return newInstanceOf((Class<T>)Class.forName(className));")));
        assertTrue(source.matches(PatternMaker.make("return clazz.getConstructor().newInstance();")));

        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testNewInstance2() throws Exception {
        class NewInstance2 {
            @SuppressWarnings({ "unused", "rawtypes" })
            void test(Class<?> clazz) {
                Class<? extends TypeConverter> pluginClass = clazz.asSubclass(TypeConverter.class);
                TypeConverter<?> converter = ReflectionUtil.instantiate(pluginClass);
            }
        }
        String internalClassName = NewInstance2.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testClassArray() throws Exception {
        class ClassArray {
            static {
                for (Class<? extends Date> dateClass : Arrays.asList(Timestamp.class, Date.class, java.sql.Date.class, Time.class)) {
                    System.out.println(dateClass);
                }
            }
        }
        String internalClassName = ClassArray.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);

        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testListArray() throws Exception {
        class ListArray {
            @SuppressWarnings("unused")
            List<List<?>> lists = Arrays.asList(new LinkedList<>(), new ArrayList<>(), new Vector<>());
        }
        String internalClassName = ListArray.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testJoin() throws Exception {
        class Join {
            @SuppressWarnings("unused")
            public String join() {
                return String.join(",", "a", "b", "c");
            }
        }
        String internalClassName = Join.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testJdbcDatabaseManager() throws Exception {
        String internalClassName = JdbcDatabaseManager.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testEmptyCollectionToArray() throws Exception {
        class EmptyCollectionToArray {
            @SuppressWarnings({ "unused", "unchecked" })
            <T> T[] toArray(final T[] a) {
                return (T[]) CollectionUtils.EMPTY_COLLECTION.toArray(a);
            }
        }
        String internalClassName = EmptyCollectionToArray.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);

        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testInnerClassConstructorInvocation() throws Exception {
        try (InputStream is = this.getClass().getResourceAsStream("/jar/inner-class-constructor-call-jdk8u331.jar")) {
            Loader loader = new ZipLoader(is);
            String internalClassName = Parameterized.class.getName().replace('.', '/');
            String source = decompileSuccess(loader, new PlainTextPrinter(), internalClassName);
            
            // Recompile decompiled source code and check errors
            assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
        }
    }
    
    @Test
    public void testBoundsAnonymous() throws Exception {
        try (InputStream is = this.getClass().getResourceAsStream("/jar/bounds-anonymous-jdk8u331.jar")) {
            Loader loader = new ZipLoader(is);
            String internalClassName = TestBoundsAnonymous.class.getName().replace('.', '/');
            String source = decompileSuccess(loader, new PlainTextPrinter(), internalClassName);
            
            // Check decompiled source code
            assertTrue(source.matches(PatternMaker.make("Iterator<Class<?>> wrapped = Collections.<Class<?>>emptySet().iterator();")));
            assertTrue(source.matches(PatternMaker.make("Iterator<Class<?>> interfaces = Collections.<Class<?>>emptySet().iterator();")));
            assertTrue(source.matches(PatternMaker.make("Class<?> nextInterface = this.interfaces.next();")));
            assertTrue(source.matches(PatternMaker.make("Class<?> nextSuperclass = TestBoundsAnonymous.wrapped.next();")));
            
            // Recompile decompiled source code and check errors
            assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
        }
    }
    
    @Test
    public void testBoundsLambda() throws Exception {
        try (InputStream is = this.getClass().getResourceAsStream("/jar/bounds-lambda-jdk8u331.jar")) {
            Loader loader = new ZipLoader(is);
            String internalClassName = TestBoundsLambda.class.getName().replace('.', '/');
            String source = decompileSuccess(loader, new PlainTextPrinter(), internalClassName);
            
            // Check decompiled source code
            assertTrue(source.matches(PatternMaker.make("Iterator<Class<?>> wrapped = Collections.<Class<?>>emptySet().iterator();")));
            assertTrue(source.matches(PatternMaker.make("Iterator interfaces = Collections.emptySet().iterator();")));
            assertTrue(source.matches(PatternMaker.make("Class<?> nextInterface = (Class)this.interfaces.next();")));
            assertTrue(source.matches(PatternMaker.make("Class<?> nextSuperclass = TestBoundsLambda.wrapped.next();")));
            
            // Recompile decompiled source code and check errors
            assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
        }
    }
    
    @Test
    public void testClassUtils() throws Exception {
        String internalClassName = ClassUtils.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);

        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testBounds2() throws Exception {
        class TestBounds2<K, V> {
            Transformer<? super V, ? extends V> valueTransformer;
            @SuppressWarnings({ "unused" })
            void putAll(K key, Iterable<? extends V> values) {
                Iterable<V> transformedValues = FluentIterable.of(values).transform(valueTransformer);
            }
        }
        
        String internalClassName = TestBounds2.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("Iterable<V> transformedValues = FluentIterable.of(values).transform(this.valueTransformer);")));
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testBounds3() throws Exception {
        enum Bounds3 {
            ;
            Deque<Bounds3> scopes;
            @SuppressWarnings("unused")
            void popScope(List<Bounds3> expected) {
                if (!EnumSet.<Bounds3>copyOf((Collection<Bounds3>)Collections.synchronizedList(expected)).contains(this.scopes.pop())) {
                    throw new IllegalStateException();
                }
            }
        }
        String internalClassName = Bounds3.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("if (!EnumSet.copyOf(Collections.synchronizedList(expected)).contains(this.scopes.pop()))")));
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testTriFunction() throws Exception {
        
        String internalClassName = TriFunction.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make(": 63 */", "Objects.requireNonNull(after);")));
        assertTrue(source.matches(PatternMaker.make(": 64 */", "return (t, u, v) -> after.apply(apply(t, u, v));")));
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testHandleExact() throws Exception {
        class HandleExact {
            @SuppressWarnings("unused")
            <T> void handleExact(MethodHandle handle, Class<T> serviceType, ClassLoader classLoader) throws Throwable {
                ServiceLoader<T> serviceLoader = (ServiceLoader<T>) handle.invokeExact(serviceType, classLoader);
            }
        }
        String internalClassName = HandleExact.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("ServiceLoader<T> serviceLoader = (ServiceLoader<T>)handle.invokeExact(serviceType, classLoader);")));
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testOverload1() throws Exception {
        interface ILogger {
            boolean isEnabled(CharSequence message, Throwable t);

            boolean isEnabled(Object message, Throwable t);

            @SuppressWarnings("unused")
            abstract class TestOverload implements ILogger {
                public boolean isEnabled() {
                    return isEnabled((Object) null, null);
                }
            }
        }
        String internalClassName = ILogger.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("return isEnabled((Object)null, (Throwable)null);")));
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testOverload2() throws Exception {
        @SuppressWarnings("serial")
        abstract class Overload extends AbstractLogger {
            @Override
            public void printf(final Level level, final String format, final Object... params) {
                if (isEnabled(level, null, format, params)) {
                }
            }
        }
        String internalClassName = Overload.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testSuperDefault() throws Exception {
        class SuperDefault implements IDefault {
            public void test(Object... o) {
                IDefault.super.test(o);
            }
        }
        String internalClassName = SuperDefault.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("IDefault.super.test(o);")));
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testVarArgDefault() throws Exception {
        String internalClassName = IDefault.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("default void test(Object... o) {}")));
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testLambdaStackWalker1() throws Exception {
        try (InputStream is = this.getClass().getResourceAsStream("/jar/lambda-stackwalker-jdk17.0.1.jar")) {
            Loader loader = new ZipLoader(is);
            String internalClassName = LambdaStackWalker1.class.getName().replace('.', '/');
            String source = decompileSuccess(loader, new PlainTextPrinter(), internalClassName);
            
            // Check decompiled source code
            assertTrue(source.matches(PatternMaker.make("return StackWalker.getInstance().walk(s -> s.map(StackWalker.StackFrame::getDeclaringClass).dropWhile(clazz -> !sentinelClass.equals(clazz)).findFirst().orElse(null));")));
            
            // Recompile decompiled source code and check errors
            assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
        }
    }

    @Test
    public void testLambdaStackWalker2() throws Exception {
        try (InputStream is = this.getClass().getResourceAsStream("/jar/lambda-stackwalker2-jdk17.0.1.jar")) {
            Loader loader = new ZipLoader(is);
            String internalClassName = LambdaStackWalker2.class.getName().replace('.', '/');
            String source = decompileSuccess(loader, new PlainTextPrinter(), internalClassName);
            
            // Check decompiled source code
            assertTrue(source.matches(PatternMaker.make("return StackWalker.getInstance().walk(s -> s.findFirst()).map(s -> s.getDeclaringClass()).orElse(null);")));
            assertTrue(source.matches(PatternMaker.make("return StackWalker.getInstance().walk(s -> s.findFirst()).map(StackWalker.StackFrame::getDeclaringClass).orElse(null);")));
            
            // Recompile decompiled source code and check errors
            assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
        }
    }
    
    public void testNoDiamondJDK6() throws Exception {
        try (InputStream is = this.getClass().getResourceAsStream("/jar/entries-test-jdk6u119.jar")) {
            Loader loader = new ZipLoader(is);
            String internalClassName = Entries.class.getName().replace('.', '/');
            String source = decompileSuccess(loader, new PlainTextPrinter(), internalClassName);
            
            // Check decompiled source code
            assertTrue(source.matches(PatternMaker.make("for (Map.Entry<String, String> entry : new ArrayList<Map.Entry<String, String>>(this.entries.values()))")));
            
            // Recompile decompiled source code and check errors
            assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
        }
    }
    
    @Test
    public void testDiamond() throws Exception {
        class Entries {
            Map<String, Entry<String, String>> entries = new HashMap<>();

            @SuppressWarnings("unused")
            void test() {
                for (Map.Entry<String, String> entry : new ArrayList<>(this.entries.values())) {
                    System.out.println(entry);
                }
            }
        }
        String internalClassName = Entries.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("for (Map.Entry<String, String> entry : new ArrayList<>(this.entries.values()))")));
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testLambdaVariables() throws Exception {
        class LambdaVariables {
            @SuppressWarnings("unused")
            void test(String str, int intger) {
                char chrctr = Character.MAX_VALUE;
                CharSequence chrsq = null;
                List<Integer> lst = null;
                Runnable r = (() -> {
                    Collections.sort(lst, (a, b) -> {
                        System.out.print(intger);
                        System.out.print(chrsq);
                        System.out.print(str);
                        System.out.print(lst);
                        System.out.print(chrctr);
                        return Integer.compare(a, b);
                    });
                });
            }
        }
        String internalClassName = LambdaVariables.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("void test(String str, int intger) {")));
        assertTrue(source.matches(PatternMaker.make("  char chrctr = Character.MAX_VALUE;")));
        assertTrue(source.matches(PatternMaker.make("  CharSequence chrsq = null;")));
        assertTrue(source.matches(PatternMaker.make("  List<Integer> lst = null;")));
        assertTrue(source.matches(PatternMaker.make("  Runnable r = () -> Collections.sort(lst, (a, b) -> {")));
        assertTrue(source.matches(PatternMaker.make("        System.out.print(intger);")));
        assertTrue(source.matches(PatternMaker.make("        System.out.print(chrsq);")));
        assertTrue(source.matches(PatternMaker.make("        System.out.print(str);")));
        assertTrue(source.matches(PatternMaker.make("        System.out.print(lst);")));
        assertTrue(source.matches(PatternMaker.make("        System.out.print(chrctr);")));
        assertTrue(source.matches(PatternMaker.make("        return Integer.compare(a, b);")));

        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testEmptyEnum() throws Exception {
        enum EmptyEnum {
            ;
            @SuppressWarnings("unused")
            static final int A = 0;
        }
        String internalClassName = EmptyEnum.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testBuilder() throws Exception {
        String internalClassName = FileAppender.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testMapUtils() throws Exception {
        String internalClassName = MapUtils.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);

        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testIterableUtils() throws Exception {
        abstract class IterableUtils {
            @SuppressWarnings({ "unused", "unchecked" })
            <O, R extends Collection<O>> List<R> partition(Iterable<? extends O> iterable, Factory<R> partitionFactory, Predicate<? super O>... predicates) {
                R singlePartition = partitionFactory.create();
                CollectionUtils.addAll(singlePartition, iterable);
                return Collections.singletonList(singlePartition);
            }
        }
        String internalClassName = IterableUtils.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Check decompiled source code
        assertTrue(source.matches(PatternMaker.make("R singlePartition = partitionFactory.create();")));
        assertTrue(source.matches(PatternMaker.make("CollectionUtils.addAll(singlePartition, iterable);")));
        assertTrue(source.matches(PatternMaker.make("return Collections.singletonList(singlePartition);")));

        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    public void testSerializedForm() throws Exception {
        class SerializedForm {

            @SuppressWarnings("unused")
            List<Failure> fFailures;

            @SuppressWarnings("unused")
            SerializedForm(Result result) {
                fFailures = Collections.synchronizedList(new ArrayList<Failure>(result.getFailures()));
            }
        }
        String internalClassName = SerializedForm.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }
    
    @Test
    public void testJSONUtils() throws Exception {
        String internalClassName = JSONUtils.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

    @Test
    public void testLambdaRenameVariables() throws Exception {
        class LambdaRenameVariables {
            @SuppressWarnings({ "unused", "unchecked", "rawtypes" })
            private void test(Object ref, Map map, Object key) {
                if (ref == null) {
                  Object ctx = new Object();
                  map.computeIfAbsent(key, k -> ctx);
                } 
                Object ctx = new Object();
              }
        }
        String internalClassName = LambdaRenameVariables.class.getName().replace('.', '/');
        String source = decompileSuccess(new ClassPathLoader(), new PlainTextPrinter(), internalClassName);
        
        // Recompile decompiled source code and check errors
        assertTrue(CompilerUtil.compile("1.8", new InMemoryJavaSourceFileObject(internalClassName, source)));
    }

}
