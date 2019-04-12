import java.util.*;
import java.util.regex.*;
import java.io.*;
import java.util.function.*;

public class SymbolBalance {
  /* a map of matching bracket-comment pairs */
  private static Map<String, String> pairs = Map.of(
  "}", "{",
  ")", "(",
  "]", "[",
  "*/", "/*");
  /* necessary variables moved to global scope so that tree functions can access them */
  private static int lineCount;
  private static LinkedList<Node> stack;
  private static ErrorCode error;
  /**
  *	@param Name of text file to check for balanced symbols
  *	@return Return an ErrorCode object with the following specifications:
  *	code = 0 if symbols are properly balanced.
  *	code = 1 if an opening symbol has no matching closing symbol. Return the
  *	symbol as openSymbol
  *	code = 2 if a closing symbol has no matching opening symbol. Return the
  *	symbol as closeSymbol
  *	code = 3 if two symbols mismatch. Return the opening symbol as openSymbol
  *	and the closing symbol as closeSymbol.
  *	You should also return the line number at which you detect the error
  *	as the lineNumber variable. The first line is 1.
  */

  public static ErrorCode balanceSymbols(String fileName) {
    error = new ErrorCode();
    error.code = -1;
    stack = new LinkedList<Node>();
    lineCount = 1;

    DecisionTree theTree = constructTree();

    try {
      File inFile = new File(fileName);
      /* outer scanner and while loop feed inner loop line by line,
      * to make keeping track of line number easier */
      Scanner input = new Scanner(inFile);

      /* ugly regex pattern of all the characters we care about */
      Pattern p = Pattern.compile("[\\{\\}\\[\\]\\(\\)]|(\\/\\*)|(\\*\\/)|\"|\n|\r]");

      while (input.hasNextLine() && error.code < 0) {

        Matcher m = p.matcher(input.nextLine());

        /* advance by match */
        while (m.find()) {
          String str = m.group();
          theTree.comparator = str;
          theTree.traverse();
        }
        lineCount++;
      }

      if (error.code < 0) {
        //all good!
        if (stack.size() == 0)
        error.code = 0;
        //if symbol is still on stack, error 1
        else {
          error.code = 1;
          error.openSymbol = stack.peek().data;
          error.lineNumber = stack.peek().index;
        }
      }
    }
    catch (FileNotFoundException f) {
      System.out.println("Invalid file: " + f.getMessage());
    }
    return error;
  }

  public static void main(String[] args)
  {
    System.out.println(balanceSymbols(args[0]));
  }

  /* For storing symbols in the stack along with their line numbers.
  * Necessary for if the loop continues through the input while an open
  * bracket remains unmatched on top of the stack. */
  private static class Node {
    public String data;
    public int index;

    public Node (String d, int i) {
      data = d;
      index = i;
    }
  }

  /* construct the functions and nodes and assemble the tree, from bottom */
  public static DecisionTree constructTree() {

    /* first build the functions */
    Supplier<Integer> error1 = () -> {
      error.code = 1;
      error.openSymbol = stack.peek().data;
      error.lineNumber = stack.peek().index;
      return error.code;
    };

    Consumer<String> error2 = (s) -> {
      error.code = 2;
      error.lineNumber = lineCount;
      error.closeSymbol = s;
    };

    Supplier<String> stackPop = () -> {
      String s = stack.pop().data;
      return s;
    };

    Function<String, Boolean> mapMatch = s ->
    pairs.get(s).equals(stack.peek().data);

    Consumer<String> stackPush = s -> stack.push(new Node(s, lineCount));

    Function<String, Boolean> closeType = s -> pairs.containsKey(s);

    Function<String, Boolean> peekMatch = s -> {
      String x = stack.peek().data; //open cmt or "
      return ((x.equals("/*") && s.equals("*/")) || (x.equals("\"") && s.equals("\"")));
    };

    Supplier<Boolean> peekCmt = () ->
    stack.peek().data.equals("/*") || stack.peek().data.equals("\"");

    Supplier<Boolean> stackEmpty = () -> stack.size() == 0;

    Supplier<Boolean> peekQt = () -> (!stackEmpty.get() && stack.peek().data.equals("\""));

    Function<String, Boolean> newLine = (s) -> s.matches("(\r|\n)");

    /* then construct and connect the nodes, bottom to top.
    * nodes and leaves have names to make debugging easier (can print traverses) */
    DecisionLeaf lvl5_ln = new DecisionLeaf("lvl5_ln", error2);
    DecisionLeaf lvl5_ly = new DecisionLeaf("lvl5_ly", stackPop);
    DecisionNode lvl4_y = new DecisionNode("lvl4_y", mapMatch);
    lvl4_y.connect(lvl5_ln, lvl5_ly);

    DecisionLeaf lvl4_ln = new DecisionLeaf("lvl4_ln", stackPush);
    DecisionNode lvl3_n = new DecisionNode("lvl3_n", closeType);
    lvl3_n.connect(lvl4_ln, lvl4_y);

    DecisionLeaf lvl4_ly = new DecisionLeaf("lvl4_ly", stackPop);
    DecisionNode lvl3_y = new DecisionNode("lvl3_y", peekMatch);
    lvl3_y.connect(null, lvl4_ly);

    DecisionNode lvl2_n = new DecisionNode("lvl2_n", peekCmt);
    lvl2_n.connect(lvl3_n, lvl3_y);

    DecisionLeaf lvl3_ln = new DecisionLeaf("lvl3_ln", stackPush);
    DecisionLeaf lvl3_ly = new DecisionLeaf("lvl3_ly", error2);
    DecisionNode lvl2_y = new DecisionNode("lvl2_y", closeType);
    lvl2_y.connect(lvl3_ln, lvl3_ly);

    DecisionNode lvl1_n = new DecisionNode("lvl1_n", stackEmpty);
    lvl1_n.connect(lvl2_n, lvl2_y);

    DecisionLeaf lvl2_ly = new DecisionLeaf("lvl2_ly", error1);

    DecisionNode lvl1_y = new DecisionNode("lvl1_y", peekQt);
    lvl1_y.connect(null, lvl2_ly);

    DecisionNode lvl0 = new DecisionNode ("lvl0", newLine);
    lvl0.connect(lvl1_n, lvl1_y);

    /* lastly, instantiate tree object with root node (and dummy String) */
    DecisionTree tree = new DecisionTree("-1", lvl0);
    return tree;
  }

  //build your own decision tree!
  public static class DecisionNode {
    private String name;
    private String comp;
    /* for some reason, function, supplier and consumer do not inherit from
    * some parent class, so all of these instance variables are necessary */
    private Function func;
    private Supplier sup;
    private Consumer cons;
    //left for no, right for yes
    private DecisionNode no;
    private DecisionNode yes;

    private DecisionNode(String n, Object f) throws IllegalArgumentException {
      /* assign function object to appropriate instance variable */
      if (f instanceof Function) func = (Function) f;
      else if (f instanceof Supplier) sup = (Supplier) f;
      /* only leaves accept consumers; nodes must pass something down */
      else if (!(f instanceof Consumer))
      throw new IllegalArgumentException("function, supplier, or consumer only, please");
      name = n;
    }

    private void connect (DecisionNode n, DecisionNode y) {
      no = n;
      yes = y;
    }
    /* for use by leaf constructor, as consumer instance variable is private in
    * node class */
    public void setCons (Consumer c) {
      cons = c;
    }
    /* recursion! make decisions by traversing the tree; yes (true) is right,
    * no (false) is left. Do / return something when you hit a leaf. */
    private void nodeTraverse(String s) {
      if (this instanceof DecisionLeaf) {
        if (sup != null) sup.get(); else cons.accept(s);
      }
      else {
        comp = s;
        Boolean b = (func != null) ? (Boolean) func.apply(comp) : (Boolean) sup.get();
        if (b && yes != null) yes.nodeTraverse(comp);
        else if (!b && no != null) no.nodeTraverse(comp);
      }
    }
  }

  public static class DecisionLeaf extends DecisionNode {

    private DecisionLeaf (String s, Object f) {
      super(s, f);
      if (f instanceof Consumer) setCons((Consumer) f);
    }
  }
  /* not recursively defined; only includes string for comparison and root node */
  public static class DecisionTree {

    private String comparator;
    private DecisionNode root;

    private DecisionTree (String s, DecisionNode r) {
      comparator = s;
      root = r;
    }

    private void traverse() {
      root.comp = comparator;
      root.nodeTraverse(comparator);
    }
  }

  /* ---- DON'T CODE BELOW THIS LINE ---- */
  private static class ErrorCode {
    public int code;
    public int lineNumber;
    public String openSymbol;
    public String closeSymbol;

    public ErrorCode(int code, int lineNumber, String openSymbol, String closeSymbol) {
      this.code = code;
      this.lineNumber = lineNumber;
      this.openSymbol = openSymbol;
      this.closeSymbol = closeSymbol;
    }

    public ErrorCode() {
      // returns empty object to be filled in
    }

    public String toString() {
      switch(code) {
        case 0: return "Success! Symbols are balanced.";
        case 1: return "Unbalanced! At " + lineNumber + " Symbol " +
        openSymbol + " has no matching closing symbol.";

        case 2: return "Unbalanced! At " + lineNumber + " Symbol " +
        closeSymbol + " has no matching opening symbol.";

        case 3: return "Unbalanced! At " + lineNumber + " Symbol " +
        openSymbol + " matches with " + closeSymbol;

        default: return "Invalid error code!";
      }
    }
  }
}
