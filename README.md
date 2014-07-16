
Get it running:
---------------

The best way for this code to be used, is as a Maven dependency in a Java project.

Firstly, you will need to delete this from the pom.xml file:
```
<parent>
  <version>1.0.0</version>
  <groupId>uk.ac.susx.tag</groupId>
  <artifactId>tag-dist</artifactId>
  <relativePath>../../tag-dist</relativePath>
</parent>
```

This is a reference to a file that usually contains information about our Maven deployment. You won't need it, and Maven will complain if you try to compile it with this here, because it's referencing a file that you don't have.

Next, open up a terminal session and set your working directory to be the project directory containing the pom.xml file.

Execute the following command:

mvn install

Next, create a new Maven project (e.g. using IntelliJ). In your project pom.xml file, add the following dependency:

```
<dependencies>
  <dependency>
    <groupId>uk.ac.susx.tag</groupId>
    <artifactId>dependencyparser</artifactId>
    <version>x.x.x</version>
  </dependency>
</dependencies>
```

Replace "x.x.x" with the relevant version number (the same version number that was reported after the mvn install).

The parser does not ship with any trained models, so you'll need to train a parser before you can use the parse methods. See the Parser.java source file comments for details.


Help file
---------

Run the static method ```printHelpfileAndOptions()``` on the Parser class to see a help file which details the general structure and usage of the parser. 

If you have access to the source, then this help file is located in: ```src/main/resources```