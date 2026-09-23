import java.io.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;

/** Read-only compatibility diagnostics. Output stays local because it describes host bytecode. */
public class DexInspect {
    public static void main(String[] args) throws Exception {
        var container = DexFileFactory.loadDexContainer(new File(args[0]), Opcodes.getDefault());
        for (String dexName : container.getDexEntryNames()) {
            for (ClassDef c : container.getEntry(dexName).getDexFile().getClasses()) {
                boolean match = c.getType().matches(args[1]);
                if (!match && args.length > 2) for (Method m : c.getMethods()) {
                    if (m.getImplementation() == null) continue;
                    for (Instruction i : m.getImplementation().getInstructions())
                        if (i instanceof ReferenceInstruction r && r.getReference() instanceof StringReference s && s.getString().contains(args[2])) match = true;
                }
                if (!match) continue;
                System.out.println("CLASS " + c.getType() + " EXTENDS " + c.getSuperclass());
                for (Field f : c.getFields()) System.out.println(" FIELD " + f);
                for (Method m : c.getMethods()) {
                    System.out.println(" METHOD " + m);
                    if (m.getImplementation() == null) continue;
                    System.out.println(" REGISTERS " + m.getImplementation().getRegisterCount());
                    for (Instruction i : m.getImplementation().getInstructions()) {
                        String extra = "";
                        if (i instanceof ReferenceInstruction r) extra += " " + r.getReference();
                        if (i instanceof OneRegisterInstruction r) extra += " A=" + r.getRegisterA();
                        if (i instanceof TwoRegisterInstruction r) extra += " B=" + r.getRegisterB();
                        if (i instanceof RegisterRangeInstruction r) extra += " start=" + r.getStartRegister() + " count=" + r.getRegisterCount();
                        if (i instanceof FiveRegisterInstruction r) extra += " regs=" + r.getRegisterC() + "," + r.getRegisterD() + "," + r.getRegisterE() + "," + r.getRegisterF() + "," + r.getRegisterG();
                        System.out.println("  " + i.getOpcode().name + extra);
                    }
                }
            }
        }
    }
}
