package org.pepsoft.worldpainter;

import org.pepsoft.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;

/**
 * The folder of the custom brushes, in the settings of WorldPainter: the images in it are the brushes of the
 * "Custom Brushes" group, and each folder in it is a group of its own. These are the changes the brush panels make to
 * it; nothing is ever deleted, only moved to the Trash.
 */
final class BrushFolders {
    private BrushFolders() {
        // Prevent instantiation
    }

    static File getBrushesDir() {
        return new File(Configuration.getConfigDir(), "brushes");
    }

    /**
     * The brushes folder of the original WorldPainter, whose settings this one keeps apart from its own.
     */
    static File getOriginalBrushesDir() {
        return new File(Configuration.getOriginalConfigDir(), "brushes");
    }

    /**
     * Whether a file can be a brush: an image which Java can read.
     */
    static boolean isImage(File file) {
        final String name = file.getName().toLowerCase(Locale.ROOT);
        return Arrays.stream(ImageIO.getReaderFileSuffixes()).anyMatch(suffix -> name.endsWith("." + suffix.toLowerCase(Locale.ROOT)));
    }

    /**
     * Copy the brushes of the original WorldPainter, leaving out those which are already here.
     *
     * @return The number of images copied.
     */
    static int copyOriginalBrushes() throws IOException {
        return copyMissing(getOriginalBrushesDir(), getBrushesDir());
    }

    private static int copyMissing(File from, File to) throws IOException {
        final File[] files = from.listFiles();
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (File file: files) {
            final File target = new File(to, file.getName());
            if (file.isDirectory()) {
                count += copyMissing(file, target);
            } else if (file.isFile() && (! target.exists())) {
                to.mkdirs();
                FileUtils.copyFileToDir(file, to);
                if (isImage(file)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Copy an image into a group, under a name which is not taken yet.
     *
     * @return The new file.
     */
    static File copyInto(File image, File groupDir) throws IOException {
        groupDir.mkdirs();
        final File target = freeName(groupDir, image.getName());
        Files.copy(image.toPath(), target.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        return target;
    }

    /**
     * Move a brush to another group, under a name which is not taken yet there.
     *
     * @return The new file.
     */
    static File moveInto(File image, File groupDir) throws IOException {
        groupDir.mkdirs();
        final File target = freeName(groupDir, image.getName());
        Files.move(image.toPath(), target.toPath());
        return target;
    }

    /**
     * Make a new group.
     *
     * @return The folder of the group.
     */
    static File createGroup(String name) throws IOException {
        final File dir = new File(getBrushesDir(), checkName(name));
        if (dir.exists()) {
            throw new IOException("There is already a group called “" + name + "”");
        }
        if (! dir.mkdirs()) {
            throw new IOException("Could not make the folder " + dir);
        }
        return dir;
    }

    /**
     * Rename a group.
     *
     * @return The new folder of the group.
     */
    static File renameGroup(File groupDir, String name) throws IOException {
        final File target = new File(groupDir.getParentFile(), checkName(name));
        if (target.exists()) {
            throw new IOException("There is already a group called “" + name + "”");
        }
        Files.move(groupDir.toPath(), target.toPath());
        return target;
    }

    /**
     * Whether files can be moved to the Trash on this system.
     */
    static boolean canTrash() {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
    }

    /**
     * Move a brush, or a group with its brushes, to the Trash, from where it can be put back.
     */
    static void trash(File file) throws IOException {
        if ((! canTrash()) || (! Desktop.getDesktop().moveToTrash(file))) {
            throw new IOException("Could not move " + file + " to the Trash");
        }
        logger.info("Moved {} to the Trash", file);
    }

    private static String checkName(String name) throws IOException {
        final String trimmed = (name != null) ? name.trim() : "";
        if (trimmed.isEmpty() || trimmed.startsWith(".") || trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains(":")) {
            throw new IOException("“" + trimmed + "” cannot be the name of a group");
        }
        return trimmed;
    }

    private static File freeName(File dir, String name) {
        File target = new File(dir, name);
        if (! target.exists()) {
            return target;
        }
        final int dot = name.lastIndexOf('.');
        final String base = (dot > 0) ? name.substring(0, dot) : name, extension = (dot > 0) ? name.substring(dot) : "";
        for (int i = 2; ; i++) {
            target = new File(dir, base + " " + i + extension);
            if (! target.exists()) {
                return target;
            }
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(BrushFolders.class);
}
