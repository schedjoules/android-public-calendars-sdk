/*
 * Copyright (C) 2013 Marten Gajda <marten@dmfs.org>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA
 */

package com.schedjoules.demo;

import org.dmfs.android.authenticator.obfuscater.AbstractObfuscater;

import android.content.Context;
import android.util.Base64;


/**
 * A very simple obfuscater that uses the XOr operation to modify the plain text and Base64 to encode the result. It slightly more "secure" than the simple
 * {@link Base64Obfuscater}, but you can still attack is easily, since there is no randomness in it.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public final class XOrObfuscater extends AbstractObfuscater
{

	/**
	 * A key that's used to Xor the value. You probably want to change this one, to make it slightly more difficult to deobfuscate your secrets.
	 * <p>
	 * Created with:
	 * </p>
	 * 
	 * <pre>
	 * dd if=/dev/urandom bs=128 count=1 | base64 -w0
	 * </pre>
	 */
	private final static byte[] KEY1 = "SHbDhNp3BdmyAHP7zcRHZBrGgeLBOZC3niweDr3bn4RO+NL8isXc64R1jmW4HSnHXGGo1lC475PG57KKtOE/z9FHnTjT4gs".getBytes();

	/**
	 * Another key. You probably want to change this one, to make it slightly more difficult to deobfuscate your secrets.
	 * <p>
	 * Created with:
	 * </p>
	 * 
	 * <pre>
	 * perl -e 'for my $i (0..128) { print int(rand(256))-128, ", "; }'
	 * </pre>
	 */
	private final static byte[] KEY2 = { -38, -9, -81, 80, 101, 22, 33, -18, 126, 103, 109, -118, 0, -68, -29, 32, -22, -34, -87, 62, 82, -3, 102, 3, 94, 61,
		-122, -112, 111, 82, 25, 21, -85, 36, -34, -6, 83, 114, 91, 67, 109, 32, 67, 82, -52, -66, 75, -31, 90, -8, -76, -7, -113, 114, -60, 97, -55, 11, -42,
		47, -77, 98, -32, -43, 123, 116, 12, 29, -31, 48, 102, 18, 63, -84, 74, -99, -102, 25, 113, 91, -57, -78, 55, 71, -58, 18, -47, 33, 69, 74, 16, -37,
		-90, -72, -14, -95, 26, -51, -47, -53, -19, -40, -88, -30, -36, 35, 65, 23, 114, -122, -64, -56, 123, -25, -12, -110, 100, -90, 27, -29, 33, 121, -79,
		20, 113, -110, 8, -89, -75 };


	/**
	 * Initialize the instance.
	 */
	public XOrObfuscater()
	{
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.dmfs.android.authenticator.obfuscater.AbstractObfuscater#obfuscate(android.content.Context, java.lang.String, java.lang.String)
	 */
	@Override
	public String obfuscate(Context context, String keyFragment, String plainText)
	{
		if (plainText == null || plainText.length() == 0)
		{
			return plainText;
		}

		byte[] xored = xor(plainText.getBytes(), xor(KEY1, KEY2));
		if (keyFragment != null && keyFragment.length() > 0)
		{
			xored = xor(xored, keyFragment.getBytes());
		}

		return Base64.encodeToString(xored, Base64.NO_WRAP);
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see org.dmfs.android.authenticator.obfuscator.AbstractObfuscater#deobfuscate(android.content.Context, java.lang.String, java.lang.String)
	 */
	@Override
	public String deobfuscate(Context context, String keyFragment, String obfuscatedText)
	{
		if (obfuscatedText == null || obfuscatedText.length() == 0)
		{
			return obfuscatedText;
		}

		byte[] xored = Base64.decode(obfuscatedText, Base64.NO_WRAP);

		if (keyFragment != null && keyFragment.length() > 0)
		{
			xored = xor(xored, keyFragment.getBytes());
		}

		return new String(xor(xored, xor(KEY1, KEY2)));
	}


	/**
	 * XOR's two byte arrays. The result will have the lengths of the first array. If the second array is shorter than the first one it's just wrapped around
	 * and reused.
	 * 
	 * @param first
	 *            An array of bytes.
	 * @param second
	 *            Another array of bytes.
	 */
	private static byte[] xor(byte[] first, byte[] second)
	{
		byte[] result = new byte[first.length];
		final int secondLength = second.length;
		for (int i = 0, len = first.length; i < len; i++)
		{
			result[i] = (byte) (first[i] ^ second[i % secondLength]);
		}
		return result;
	}

}
