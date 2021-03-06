package org.giiwa.net.nio;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

@ChannelHandler.Sharable
public abstract class IoHandler extends ChannelInboundHandlerAdapter {

//	private static Log log = LogFactory.getLog(IoHandler.class);

	public IoHandler() {
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
//		cause.printStackTrace();
		super.exceptionCaught(ctx, cause);
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

//		System.out.println("remove, client=" + ctx.channel().remoteAddress());

		AttributeKey<IoRequest> req = AttributeKey.valueOf("req");
		Attribute<IoRequest> req1 = ctx.channel().attr(req);
		IoRequest r1 = req1.get();
		if (r1 != null) {
			r1.release();
		}

		AttributeKey<IoResponse> resp = AttributeKey.valueOf("resp");
		Attribute<IoResponse> resp1 = ctx.channel().attr(resp);
		IoResponse r2 = resp1.get();
		if (r2 != null) {
			r2.release();
		}

		super.handlerRemoved(ctx);

	}

	public void channelRead(ChannelHandlerContext ctx, Object msg) {

		ByteBuf m = (ByteBuf) msg;

		try {
			AttributeKey<IoRequest> a = AttributeKey.valueOf("req");
			Attribute<IoRequest> a1 = ctx.channel().attr(a);

			IoRequest req = a1.get();
			if (req == null) {
				req = IoRequest.create(m);
				a1.set(req);
			} else {
				req.put(m);
			}

			AttributeKey<IoResponse> b = AttributeKey.valueOf("resp");
			Attribute<IoResponse> b1 = ctx.channel().attr(b);

			IoResponse resp = b1.get();
			if (resp == null) {
				resp = IoResponse.create(ctx.channel());
				b1.set(resp);
			}

//			System.out.println("got data, client=" + ctx.channel().remoteAddress());

			process(req, resp);

			req.compact();

		} finally {
			m.release();
		}

	}

	public abstract void process(IoRequest req, IoResponse resp);

}
