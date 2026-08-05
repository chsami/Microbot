package shortestpath;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * General utility helpers for I/O and primitive array manipulation used by the
 * shortest path plugin.
 */
public class Util
{
	/**
	 * Reads all bytes from the provided {@link InputStream} until EOF.
	 * This method does not close the stream; the caller retains responsibility for
	 * resource management.
	 *
	 * @param in the input stream to read from.
	 * @return a newly allocated byte array containing all bytes read (may be empty,
	 * never {@code null}).
	 * @throws IOException if an I/O error occurs while reading.
	 */
	public static byte[] readAllBytes(InputStream in) throws IOException
	{
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];

		while (true)
		{
			int read = in.read(buffer, 0, buffer.length);

			if (read == -1)
			{
				return result.toByteArray();
			}

			result.write(buffer, 0, read);
		}
	}

	/**
	 * Concatenates the contents of multiple {@code int[]} arrays into one
	 * contiguous array.
	 * {@code null} elements in {@code arrays} are skipped. If all arrays are
	 * {@code null} or empty, {@code null}
	 * is returned (this mirrors the existing behavior relied upon by callers).
	 *
	 * @param arrays an array of {@code int[]} segments to concatenate (may contain
	 *               {@code null}).
	 * @return a new combined array, or {@code null} if there are no elements to
	 * copy.
	 */
	public static int[] concatenate(int[][] arrays)
	{
		int n = 0;
		for (int[] value : arrays)
		{
			n += (value == null) ? 0 : value.length;
		}
		if (n == 0)
		{
			return null;
		}
		int[] array = new int[n];
		int k = 0;
		for (int[] ints : arrays)
		{
			if (ints != null)
			{
				for (int anInt : ints)
				{
					array[k++] = anInt;
				}
			}
		}
		return array;
	}
}
