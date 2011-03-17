package com.thebuzzmedia.redis.command;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

import com.thebuzzmedia.redis.Constants;
import com.thebuzzmedia.redis.util.ArrayUtils;
import com.thebuzzmedia.redis.util.DynamicByteArray;
import com.thebuzzmedia.redis.util.IByteArraySource;
import com.thebuzzmedia.redis.util.IDynamicArray;

public abstract class AbstractCommand implements ICommand {
	private IDynamicArray<byte[], ByteBuffer> commandBuffer;
	private Deque<IDynamicArray<byte[], ByteBuffer>> pendingArguments;

	public AbstractCommand() {
		pendingArguments = new ArrayDeque<IDynamicArray<byte[], ByteBuffer>>(4);
	}

	@Override
	public String toString() {
		return this.getClass().getName()
				+ "[command="
				+ (commandBuffer == null ? "[call getCommandData() first to generate the command]"
						: new String(commandBuffer.getArray())) + "]";
	}

	@Override
	public synchronized IByteArraySource getCommandData() {
		/*
		 * Because we need to know how many arguments are included as part of
		 * this command before formatting it into a giant Multi-Bulk query for
		 * the server, we delay building the actual command byte[] until it is
		 * requested.
		 */
		if (commandBuffer == null) {
			commandBuffer = new DynamicByteArray();

			// Prepare the MultiBulk-formatted reply
			commandBuffer
					.append(new byte[] { Constants.REPLY_TYPE_MULTI_BULK });
			commandBuffer.append(Integer.toString(pendingArguments.size())
					.getBytes());
			commandBuffer.append(Constants.CRLF_BYTES);

			// Append each of the pending arguments
			for (int i = 0, size = pendingArguments.size(); i < size; i++)
				commandBuffer.append(pendingArguments.pollFirst());

			/*
			 * The pendingArgs are empty from pollFirst, but make it easier on
			 * the GC anyway.
			 */
			pendingArguments = null;
		}

		return (DynamicByteArray) commandBuffer;
	}

	protected void append(CharSequence argument)
			throws IllegalArgumentException {
		if (argument == null || argument.length() == 0)
			return;

		IByteArraySource source = ArrayUtils.encode(argument);
		append(source.getArray(), 0, source.getLength());
	}

	protected void append(byte[] argument) throws IllegalArgumentException {
		if (argument == null || argument.length == 0)
			return;

		append(argument, 0, argument.length);
	}

	protected void append(byte[] argument, int index, int length)
			throws IllegalArgumentException {
		if (argument == null || length == 0)
			return;
		if (index < 0 || (index + length) > argument.length)
			throw new IllegalArgumentException("index [" + index
					+ "] must be >= 0 and (index + length) ["
					+ (index + length) + "] must be <= argument.length ["
					+ argument.length + "]");

		IDynamicArray<byte[], ByteBuffer> array = new DynamicByteArray();

		// First, append the Bulk-formatted length header
		array.append(new byte[] { Constants.REPLY_TYPE_BULK });
		// array.append(ArrayUtils.intToByteArray(argument.length));
		array.append(Integer.toString(length).getBytes());
		array.append(Constants.CRLF_BYTES);

		// Second, append the Bulk-formatted data payload
		array.append(argument, index, length);
		array.append(Constants.CRLF_BYTES);

		// Add the array to our pending args that will be sent
		pendingArguments.add(array);
	}
}