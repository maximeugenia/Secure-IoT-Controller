# Exmaple makefile from
#	https://www.cs.swarthmore.edu/~newhall/unixhelp/javamakefiles.html

JFLAGS = -g -cp
JC = javac
JAVA = java
CONTROLLER = Controller
CLASSPATH = '.:lib/gson-2.8.6.jar'
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $(CLASSPATH) $*.java

CLASSES = \
    Server.java \
	outputs/RuleOutput.java \
	outputs/Status.java \
	outputs/StatusOutput.java \
	ControllerState.java \
	Delegation.java \
	Rule.java \
	Parser.java \
	Tester.java

default: classes

classes: $(CLASSES:.java=.class)

run: default
	@read -p "Enter path for config file:" config; \
	$(JAVA) -cp $(CLASSPATH) Controller 3000 $$config;

clean:
	$(RM) $(CLASSES:.java=.class) *.class

test: default
	$(JAVA) -cp $(CLASSPATH) Tester