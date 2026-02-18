package tools;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import java.nio.file.*;
import java.util.stream.*;

public class UMLGenerator {
    public static void main(String[] args) throws Exception {
        Path folder = Paths.get(args[0]);
        Files.walk(folder)
             .filter(p -> p.toString().endsWith(".java"))
             .forEach(p -> {
                 try {
                     CompilationUnit cu = StaticJavaParser.parse(p);
                     cu.getTypes().forEach(t -> {
                         String type = t.isInterface() ? "interface" : "class";
                         System.out.println(type + " " + t.getName());
                         t.getExtendedTypes().forEach(e -> System.out.println(t.getName() + " --|> " + e));
                         t.getImplementedTypes().forEach(i -> System.out.println(t.getName() + " ..|> " + i));
                         t.getMembers().forEach(m -> {
                             if (m instanceof FieldDeclaration) {
                                 ((FieldDeclaration)m).getVariables().forEach(v ->
                                     System.out.println(t.getName() + " : " + v.getName())
                                 );
                             } else if (m instanceof MethodDeclaration) {
                                 MethodDeclaration md = (MethodDeclaration)m;
                                 System.out.println(t.getName() + " : " + md.getName() + "()");
                             }
                         });
                     });
                 } catch(Exception e) { e.printStackTrace(); }
             });
    }
}
