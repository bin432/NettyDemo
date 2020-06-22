package cpf;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CpfServerHandler extends ChannelInboundHandlerAdapter {

    protected static final int MaxLineBytes = 1024;

    protected ByteArrayOutputStream lineBuf;
    protected Mode mode = Mode.Command;
    protected String name;
    protected RandomAccessFile file;
    protected MappedByteBuffer mapBuf;

    enum Mode{
        Command,
        PutMode,
        ChunkData,
    }

    public CpfServerHandler() {

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.close();
        cause.printStackTrace();
        System.out.println("catch");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(Unpooled.copiedBuffer("hello\r\n", CharsetUtil.UTF_8));

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ctx.close();
        System.out.println("close");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf in = (ByteBuf)msg;// 转化为ByteBuf
        boolean ww = in.isWritable();
        try {
            while (in.isReadable()) {
                switch (this.mode) {
                    case Command:
                        String line = readLine(ctx, in);
                        if(line == null) {
                            break;
                        }
                        handleCommand(ctx, line);
                        break;
                    case PutMode:
                        String si = readLine(ctx, in);
                        if(si == null)
                            break;
                        int size = Integer.parseInt(si, 16);
                        this.file = new RandomAccessFile(this.name, "rw");
                        FileChannel fl = this.file.getChannel();
                        mapBuf = fl.map(FileChannel.MapMode.READ_WRITE, this.file.length(), size);
                        this.mode = Mode.ChunkData;
                        break;
                    case ChunkData:
                        int remaining = this.mapBuf.remaining();
                        if(in.readableBytes() <= remaining) {
                            in.readBytes(this.mapBuf);
                        } else {
                            in.readRetainedSlice(remaining).readBytes(this.mapBuf);
                        }

                        break;
                }
            }


            //ctx.writeAndFlush(Unpooled.copiedBuffer("hello", CharsetUtil.UTF_8));
            int i = 9;
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private void handleCommand(ChannelHandlerContext ctx, String line) {
        String command;
        int pos = line.indexOf(" ");
        if(pos > 0) {
            command = line.substring(0, pos);
        } else {
            command = line;
        }

        String arg1;
        String arg2;
        int pos2 = line.indexOf(" ", pos + 1);
        if(pos2 > 0) {
            arg1 = line.substring(pos+1, pos2);
            arg2 = line.substring(pos2+1);
        } else {
            arg1 = line.substring(pos+1);
            arg2 = "";
        }

        switch (command.toUpperCase()) {
            case "PUT":
                handlePut(ctx, arg1, arg2);
                break;
            case "DEL":
                handleDel(ctx, arg1);
                break;
            case "QUIT":
                sendResponse(ctx,"+OK good bye");
                ctx.close();
                break;
            default:
                sendResponse(ctx, "not the command.");
                break;
        }
    }

    private void handlePut(ChannelHandlerContext ctx, String name, String mode) {
        try {
            this.name = name;
            Path p = Paths.get(name);
            long off = 0;
            if(!Files.exists(p)) {
                Files.createFile(p);
            } else {
                off = Files.size(p);
            }
            this.mode = Mode.PutMode;
            sendResponse(ctx, "+OK "+String.valueOf(off));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleDel(ChannelHandlerContext ctx, String name) {
        try {
            Files.deleteIfExists(Paths.get(name));
            sendResponse(ctx, "+OK ");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, String resp) {
        byte[] respBytes = resp.getBytes(CharsetUtil.UTF_8);
        ByteBuf buf = ctx.alloc().directBuffer(respBytes.length + 2);
        buf.writeBytes(respBytes);
        buf.writeByte('\r');
        buf.writeByte('\n');

        ctx.writeAndFlush(buf);
    }

    private String readLine(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
        int index = buf.readerIndex();
        int count = buf.readableBytes();
        int eol = buf.forEachByte((b)-> b != '\n');
        if(eol < 0) {
            if(lineBuf == null) {
                lineBuf = new ByteArrayOutputStream(MaxLineBytes);
            }

            if(count + lineBuf.size() > MaxLineBytes) {
                // 超出最大长度 就 直接 断开链接
                throw new Exception("MaxLineBytes error");
            }
            buf.readBytes(lineBuf, count);
            return null;
        }

        final int del = eol > 0 && buf.getByte(eol-1) == '\r'? 2 : 1;
        final int length = eol - index - del + 1;

        if(lineBuf != null) {
            buf.readBytes(lineBuf, length);
            buf.skipBytes(del);

            return lineBuf.toString("utf-8");
        }

        String line = buf.toString(index, length, CharsetUtil.UTF_8);
        buf.skipBytes(length + del);
        return line;
    }
}
