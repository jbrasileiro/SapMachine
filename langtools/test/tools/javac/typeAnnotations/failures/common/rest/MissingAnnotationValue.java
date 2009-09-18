/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary check for missing annotation value
 * @author Mahmood Ali
 * @compile/fail/ref=MissingAnnotationValue.out -XDrawDiagnostics -source 1.7 MissingAnnotationValue.java
 */
class MissingAnnotationValue {
  void test() {
    new @A String();
  }
}

@interface A { int field(); }
