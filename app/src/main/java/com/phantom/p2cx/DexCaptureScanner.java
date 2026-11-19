package com.phantom.p2cx;

import android.content.pm.ApplicationInfo;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Locates CameraX's capture implementation by its shipped DEX logging marker. */
final class DexCaptureScanner {
    static Set<String> find(ApplicationInfo app) throws Exception {
        Set<String> result = new LinkedHashSet<>();
        List<String> paths = new ArrayList<>();
        paths.add(app.sourceDir);
        if (app.splitSourceDirs != null) java.util.Collections.addAll(paths, app.splitSourceDirs);
        for (String path : paths) {
            try (ZipFile apk = new ZipFile(path)) {
                java.util.Enumeration<? extends ZipEntry> entries = apk.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                    if (entry.getSize() > 96L * 1024 * 1024) continue;
                    try (InputStream input = apk.getInputStream(entry)) {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        byte[] block = new byte[65536];
                        int count;
                        while ((count = input.read(block)) != -1) {
                            bytes.write(block, 0, count);
                            if (bytes.size() > 96 * 1024 * 1024) throw new java.io.IOException("DEX exceeds scan budget");
                        }
                        new Reader(bytes.toByteArray()).collect(result);
                    }
                }
            }
        }
        return result;
    }

    private static final class Reader {
        private final byte[] data;
        Reader(byte[] data) { this.data = data; }
        private int u32(int offset) {
            if (offset < 0 || offset > data.length - 4) throw new IllegalArgumentException("Invalid DEX offset");
            return (data[offset] & 255) | ((data[offset + 1] & 255) << 8)
                | ((data[offset + 2] & 255) << 16) | ((data[offset + 3] & 255) << 24);
        }
        private int leb(int[] cursor) {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int part = data[cursor[0]++] & 255;
                value |= (part & 127) << shift;
                if ((part & 128) == 0) return value;
            }
            throw new IllegalArgumentException("Invalid DEX ULEB128");
        }
        private String string(int index) {
            int[] cursor = {u32(u32(60) + index * 4)};
            leb(cursor);
            int end = cursor[0];
            while (data[end] != 0) end++;
            return new String(data, cursor[0], end - cursor[0], StandardCharsets.UTF_8);
        }
        private String type(int index) { return string(u32(u32(68) + index * 4)); }

        void collect(Set<String> classes) {
            if (data.length < 112 || data[0] != 'd' || data[1] != 'e' || data[2] != 'x') return;
            int marker = -1;
            int strings = u32(56);
            for (int index = 0; index < strings; index++) {
                if (string(index).equals("takePictureInternal")) { marker = index; break; }
            }
            if (marker < 0) return;
            int classesCount = u32(96);
            int classesOffset = u32(100);
            for (int index = 0; index < classesCount; index++) {
                int offset = classesOffset + index * 32;
                int classData = u32(offset + 24);
                if (classData == 0) continue;
                int[] cursor = {classData};
                int staticFields = leb(cursor);
                int instanceFields = leb(cursor);
                int directMethods = leb(cursor);
                int virtualMethods = leb(cursor);
                for (int field = 0; field < staticFields + instanceFields; field++) { leb(cursor); leb(cursor); }
                for (int method = 0; method < directMethods + virtualMethods; method++) {
                    leb(cursor);
                    leb(cursor);
                    int code = leb(cursor);
                    if (code == 0 || !contains(code, marker)) continue;
                    String descriptor = type(u32(offset));
                    classes.add(descriptor.substring(1, descriptor.length() - 1).replace('/', '.'));
                    break;
                }
            }
        }

        private boolean contains(int code, int marker) {
            int count = u32(code + 12);
            int start = code + 16;
            if (count < 0 || count > (data.length - start) / 2) return false;
            for (int unit = 0; unit < count - 1; unit++) {
                int position = start + unit * 2;
                int opcode = data[position] & 255;
                if (opcode == 26 && marker <= 65535) {
                    int stringIndex = (data[position + 2] & 255) | ((data[position + 3] & 255) << 8);
                    if (stringIndex == marker) return true;
                }
                if (opcode == 27 && unit < count - 2 && u32(position + 2) == marker) return true;
            }
            return false;
        }
    }
}
