package world.cryville.relay;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

public abstract class DataInputStreamUtil {
	public static void readNBytes(DataInputStream self, byte[] b, int off, int len) throws IOException {
		int read = 0;
		while (read < len) {
			int r = self.read(b, off + read, len - read);
			if (r == -1) throw new EOFException();
			read += r;
		}
	}
}
