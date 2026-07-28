package totah.lab.pocket.visualization.control;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import java.nio.file.Path;
import java.util.Optional;

import static org.lwjgl.system.MemoryUtil.memUTF8;
import static org.lwjgl.util.nfd.NativeFileDialog.NFD_CANCEL;
import static org.lwjgl.util.nfd.NativeFileDialog.NFD_ERROR;
import static org.lwjgl.util.nfd.NativeFileDialog.NFD_FreePath;
import static org.lwjgl.util.nfd.NativeFileDialog.NFD_GetError;
import static org.lwjgl.util.nfd.NativeFileDialog.NFD_Init;
import static org.lwjgl.util.nfd.NativeFileDialog.NFD_OKAY;
import static org.lwjgl.util.nfd.NativeFileDialog.NFD_PickFolder;
import static org.lwjgl.util.nfd.NativeFileDialog.NFD_Quit;

/**
 * Native folder selection through LWJGL/NFD. It shares jME's native runtime
 * and does not initialize AWT or launch another JVM.
 */
public final class NativeFolderPicker {
    private NativeFolderPicker() {
    }

    public static Optional<Path> pickFolder(Path initialDirectory) {
        int initialization = NFD_Init();
        if (initialization == NFD_ERROR) {
            throw failure();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer output = stack.mallocPointer(1);
            CharSequence initial = initialDirectory == null
                    ? null
                    : initialDirectory.toString();
            int result = NFD_PickFolder(output, initial);
            if (result == NFD_CANCEL) {
                return Optional.empty();
            }
            if (result != NFD_OKAY) {
                throw failure();
            }

            long address = output.get(0);
            try {
                return Optional.of(
                        Path.of(memUTF8(address))
                                .toAbsolutePath()
                                .normalize());
            } finally {
                NFD_FreePath(address);
            }
        } finally {
            NFD_Quit();
        }
    }

    private static IllegalStateException failure() {
        String error = NFD_GetError();
        return new IllegalStateException(
                error == null
                        ? "Native folder dialog failed"
                        : error);
    }
}
