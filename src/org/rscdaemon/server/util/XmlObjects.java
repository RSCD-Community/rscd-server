package org.rscdaemon.server.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Reads the server's definition and location documents -- the XStream XML in
 * conf/ -- without XStream.
 *
 * WHY. lib/xstream.jar is XStream 1.x from 2005. It could not even instantiate
 * these classes through its portable code path: most of the model classes have
 * no no-args constructor, so PersistenceManager had to force
 * Sun14ReflectionProvider, which allocates objects through
 * sun.reflect.ReflectionFactory. JEP 471 deprecated the sun.misc.Unsafe
 * memory-access methods behind that, JEP 498 makes them warn at runtime, and
 * they are slated to throw. A server that dies during static init on a JDK
 * upgrade is not something to leave for an operator to discover.
 *
 * The six classes that lacked a no-args constructor now declare one. XStream
 * called no constructor at all, so a constructor that does nothing reproduces
 * its behaviour exactly: every field starts at its default and the document
 * sets it.
 *
 * WHAT IT SUPPORTS. The shapes that occur across all 66 documents in conf/,
 * established by reading them rather than by guessing from the classes:
 *
 *   <map>                     33 documents, and nested as field values
 *     <entry><int>100</int><ObjectMiningDef>..</ObjectMiningDef></entry>
 *   <linked-list>              8 documents -- Shops, the three loc files
 *   <linked-list-array>        no document uses this now -- KeyChestLoot did,
 *                              as a List[], before it became a weighted table
 *                              of ChestLootDef; the path is kept because an
 *                              operator's own document may still want it
 *   <PacketHandler-array>      arrays of aliased objects
 *   <ids><int>1</int></ids>            int[] and String[] fields
 *   <items><InvItem>..</InvItem></items>   Collection fields
 *   <options><string>..</string></options>
 *
 * Elements are typed two different ways and the difference matters:
 *
 *   - a MEMBER element is named after a field (<items>, <ids>, <certs>), so its
 *     own type comes from the field's declared type;
 *   - a VALUE element is named after its type (<int>, <string>, <InvItem>,
 *     <linked-list>), which is how map keys, map values and collection
 *     elements identify themselves.
 *
 * Reading the tag for values rather than trusting a declared generic type is
 * what lets ArrayList<InvItem> and List[] work without any generics
 * reflection at all.
 *
 * There are no class= attributes, no reference= ids, no nulls and no CDATA in
 * any document, so none of XStream's machinery for those is reproduced. XML
 * comments do occur (<!-- Copper -->) and are skipped properly, terminator and
 * all, rather than by scanning for the next '>'.
 *
 * REFLECTION, DELIBERATELY NARROW. Fields are found by walking the class up its
 * superclasses with getDeclaredField -- InvItem keeps its id on Entity -- and
 * setAccessible is called because most of these fields are private. That is
 * ordinary reflection over this project's own classes in the unnamed module,
 * which no JDK restricts and which needs no --add-opens. Nothing here touches a
 * JDK internal, which is the whole point.
 *
 * An unknown element is skipped and reported once rather than throwing: a
 * document that gained a field should not stop the server booting, and one that
 * lost a field should say so instead of silently leaving a zero.
 */
public final class XmlObjects {

   /* One table per reader. The game server and the login server alias the same
      name -- "PacketHandler" -- to different classes, so a table shared between
      them would hand one of the two the wrong type the moment both ran in a
      single JVM. They are separate processes today; this makes that a choice
      rather than a requirement. */
   private final Map<String, Class<?>> aliases = new HashMap<String, Class<?>>();
   private final Set<String> warned = new HashSet<String>();
   /* Field lookups are repeated for every element of every document; the defs
      are thousands of objects, so the misses are worth remembering too. */
   private final Map<String, Field> fields = new HashMap<String, Field>();

   public void alias(String name, Class<?> type) {
      this.aliases.put(name, type);
   }

   public Object read(String xml) {
      Node root = parse(xml);
      return root == null ? null : value(root);
   }

   /*
    * ---- the document tree ----
    */
   private static final class Node {
      String name;
      String text = "";
      List<Node> children;

      List<Node> kids() {
         return this.children == null ? EMPTY : this.children;
      }
   }

   private static final List<Node> EMPTY = new ArrayList<Node>(0);

   private static Node parse(String s) {
      List<Node> stack = new ArrayList<Node>();
      Node root = null;
      int i = 0;
      int length = s.length();

      while (i < length) {
         int open = s.indexOf('<', i);
         if (open < 0) {
            break;
         }

         if (open > i && !stack.isEmpty()) {
            Node top = stack.get(stack.size() - 1);
            if (top.children == null) {
               String text = s.substring(i, open);
               // Whitespace between an open tag and a child tag is layout, not
               // content; a leaf's own text is kept exactly as written.
               if (text.trim().length() > 0 || top.text.length() == 0) {
                  top.text = decode(text);
               }
            }
         }

         /* Comments first, and to their real terminator: "<!-- a > b -->"
            contains a '>' that is not the end of anything. */
         if (s.startsWith("<!--", open)) {
            int end = s.indexOf("-->", open + 4);
            i = end < 0 ? length : end + 3;
            continue;
         }

         int close = s.indexOf('>', open);
         if (close < 0) {
            break;
         }

         String tag = s.substring(open + 1, close);
         i = close + 1;

         if (tag.startsWith("?") || tag.startsWith("!")) {
            continue;
         }

         if (tag.startsWith("/")) {
            if (!stack.isEmpty()) {
               stack.remove(stack.size() - 1);
            }

            continue;
         }

         boolean selfClosing = tag.endsWith("/");
         if (selfClosing) {
            tag = tag.substring(0, tag.length() - 1);
         }

         int space = tag.indexOf(' ');
         if (space > 0) {
            tag = tag.substring(0, space);
         }

         Node node = new Node();
         node.name = tag.trim();

         if (stack.isEmpty()) {
            if (root != null) {
               break;
            }

            root = node;
         } else {
            Node parent = stack.get(stack.size() - 1);
            if (parent.children == null) {
               parent.children = new ArrayList<Node>();
               parent.text = "";
            }

            parent.children.add(node);
         }

         if (!selfClosing) {
            stack.add(node);
         }
      }

      return root;
   }

   /*
    * ---- values: elements named after their own type ----
    */
   private Object value(Node node) {
      String tag = node.name;

      if ("int".equals(tag)) {
         return Integer.valueOf(parseInt(node.text));
      } else if ("long".equals(tag)) {
         return Long.valueOf(parseLong(node.text));
      } else if ("short".equals(tag)) {
         return Short.valueOf((short)parseInt(node.text));
      } else if ("byte".equals(tag)) {
         return Byte.valueOf((byte)parseInt(node.text));
      } else if ("boolean".equals(tag)) {
         return Boolean.valueOf("true".equals(node.text));
      } else if ("string".equals(tag)) {
         return node.text;
      } else if ("char".equals(tag)) {
         return Character.valueOf(node.text.length() == 0 ? '\0' : node.text.charAt(0));
      } else if ("double".equals(tag)) {
         return Double.valueOf(node.text.length() == 0 ? 0.0 : Double.parseDouble(node.text));
      } else if ("float".equals(tag)) {
         return Float.valueOf(node.text.length() == 0 ? 0.0F : Float.parseFloat(node.text));
      } else if ("map".equals(tag)) {
         return map(node);
      } else if ("linked-list".equals(tag)) {
         return fill(new LinkedList<Object>(), node);
      } else if ("list".equals(tag)) {
         return fill(new ArrayList<Object>(), node);
      } else if (tag.endsWith("-array")) {
         return array(node, tag.substring(0, tag.length() - 6));
      } else {
         Class<?> type = this.aliases.get(tag);
         if (type == null) {
            warn("no class registered for <" + tag + ">");
            return null;
         }

         return object(node, type);
      }
   }

   /** {@code <X-array>}, where X is an alias or a structural tag. */
   private Object array(Node node, String componentTag) {
      Class<?> component = this.aliases.get(componentTag);
      if (component == null) {
         // linked-list-array: an array of lists, as KeyChestLoot ships.
         component = "linked-list".equals(componentTag) || "list".equals(componentTag)
            ? List.class
            : scalarClass(componentTag);
      }

      if (component == null) {
         warn("no class registered for <" + componentTag + "-array>");
         return null;
      }

      List<Node> kids = node.kids();
      Object result = Array.newInstance(component, kids.size());

      for (int i = 0; i < kids.size(); i++) {
         Array.set(result, i, value(kids.get(i)));
      }

      return result;
   }

   private Class<?> scalarClass(String tag) {
      if ("int".equals(tag)) {
         return int.class;
      } else if ("long".equals(tag)) {
         return long.class;
      } else if ("boolean".equals(tag)) {
         return boolean.class;
      } else if ("string".equals(tag)) {
         return String.class;
      } else {
         return null;
      }
   }

   private Map<Object, Object> map(Node node) {
      HashMap<Object, Object> result = new HashMap<Object, Object>();

      for (Node entry : node.kids()) {
         List<Node> pair = entry.kids();
         if (pair.size() == 2) {
            result.put(value(pair.get(0)), value(pair.get(1)));
         } else {
            warn("<" + entry.name + "> has " + pair.size() + " children, expected 2");
         }
      }

      return result;
   }

   private Collection<Object> fill(Collection<Object> into, Node node) {
      for (Node kid : node.kids()) {
         into.add(value(kid));
      }

      return into;
   }

   private Object object(Node node, Class<?> type) {
      Object instance;

      try {
         java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
         constructor.setAccessible(true);
         instance = constructor.newInstance();
      } catch (Exception e) {
         warn("cannot construct " + type.getName() + ": " + e);
         return null;
      }

      for (Node child : node.kids()) {
         Field field = field(type, child.name);
         if (field == null) {
            warn(type.getSimpleName() + " has no field " + child.name);
            continue;
         }

         try {
            field.set(instance, member(child, field.getType()));
         } catch (Exception e) {
            warn("cannot set " + type.getSimpleName() + "." + child.name + ": " + e);
         }
      }

      return instance;
   }

   /*
    * ---- members: elements named after a field ----
    *
    * The declared type decides the container; the children name what goes in it.
    */
   private Object member(Node node, Class<?> type) {
      if (type.isArray()) {
         Class<?> component = type.getComponentType();
         List<Node> kids = node.kids();
         Object result = Array.newInstance(component, kids.size());

         for (int i = 0; i < kids.size(); i++) {
            Array.set(result, i, value(kids.get(i)));
         }

         return result;
      }

      if (Map.class.isAssignableFrom(type)) {
         return map(node);
      }

      if (Collection.class.isAssignableFrom(type)) {
         Collection<Object> into = LinkedList.class.isAssignableFrom(type)
            ? new LinkedList<Object>()
            : new ArrayList<Object>();
         return fill(into, node);
      }

      if (type == String.class) {
         return node.text;
      }

      if (type.isPrimitive() || isBoxed(type)) {
         return scalar(node.text, type);
      }

      // A nested object written under its field name rather than its alias.
      return object(node, type);
   }

   /** Walks up the hierarchy: InvItem keeps id on Entity. Statics are skipped. */
   private Field field(Class<?> type, String name) {
      String key = type.getName() + "#" + name;
      if (this.fields.containsKey(key)) {
         return this.fields.get(key);
      }

      Field found = null;

      for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
         try {
            Field candidate = c.getDeclaredField(name);
            if (!Modifier.isStatic(candidate.getModifiers())) {
               candidate.setAccessible(true);
               found = candidate;
            }

            break;
         } catch (NoSuchFieldException e) {
            // keep climbing
         }
      }

      this.fields.put(key, found);
      return found;
   }

   private static boolean isBoxed(Class<?> type) {
      return type == Integer.class || type == Boolean.class || type == Long.class
         || type == Byte.class || type == Short.class || type == Double.class
         || type == Float.class || type == Character.class;
   }

   private Object scalar(String text, Class<?> type) {
      if (type == boolean.class || type == Boolean.class) {
         return Boolean.valueOf("true".equals(text));
      } else if (type == int.class || type == Integer.class) {
         return Integer.valueOf(parseInt(text));
      } else if (type == long.class || type == Long.class) {
         return Long.valueOf(parseLong(text));
      } else if (type == byte.class || type == Byte.class) {
         return Byte.valueOf((byte)parseInt(text));
      } else if (type == short.class || type == Short.class) {
         return Short.valueOf((short)parseInt(text));
      } else if (type == double.class || type == Double.class) {
         return Double.valueOf(text.length() == 0 ? 0.0 : Double.parseDouble(text));
      } else if (type == float.class || type == Float.class) {
         return Float.valueOf(text.length() == 0 ? 0.0F : Float.parseFloat(text));
      } else if (type == char.class || type == Character.class) {
         return Character.valueOf(text.length() == 0 ? '\0' : text.charAt(0));
      } else {
         warn("no scalar conversion for " + type.getName());
         return null;
      }
   }

   private int parseInt(String text) {
      try {
         return text.length() == 0 ? 0 : Integer.parseInt(text.trim());
      } catch (NumberFormatException e) {
         warn("not a number: \"" + text + "\"");
         return 0;
      }
   }

   private long parseLong(String text) {
      try {
         return text.length() == 0 ? 0L : Long.parseLong(text.trim());
      } catch (NumberFormatException e) {
         warn("not a number: \"" + text + "\"");
         return 0L;
      }
   }

   private static String decode(String s) {
      int amp = s.indexOf('&');
      if (amp < 0) {
         return s;
      }

      StringBuilder out = new StringBuilder(s.length());
      out.append(s, 0, amp);

      for (int i = amp; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c != '&') {
            out.append(c);
            continue;
         }

         int end = s.indexOf(';', i);
         if (end < 0 || end - i > 10) {
            out.append(c);
            continue;
         }

         String name = s.substring(i + 1, end);
         if ("amp".equals(name)) {
            out.append('&');
         } else if ("lt".equals(name)) {
            out.append('<');
         } else if ("gt".equals(name)) {
            out.append('>');
         } else if ("quot".equals(name)) {
            out.append('"');
         } else if ("apos".equals(name)) {
            out.append('\'');
         } else if (name.startsWith("#")) {
            try {
               out.append((char)(name.startsWith("#x") || name.startsWith("#X")
                  ? Integer.parseInt(name.substring(2), 16)
                  : Integer.parseInt(name.substring(1))));
            } catch (NumberFormatException e) {
               out.append('&').append(name).append(';');
            }
         } else {
            out.append('&').append(name).append(';');
            i = end;
            continue;
         }

         i = end;
      }

      return out.toString();
   }

   private void warn(String message) {
      if (this.warned.add(message)) {
         Logger.print("XmlObjects: " + message);
      }
   }
}
