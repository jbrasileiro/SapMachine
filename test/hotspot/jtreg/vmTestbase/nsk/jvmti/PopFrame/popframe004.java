/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package nsk.jvmti.PopFrame;

import java.io.*;

/**
 * This test checks that a method's frame can be popped by the JVMTI
 * function <code>PopFrame()</code>:
 * <li>with intermediate native frames, and a thread, from which
 * the PopFrame() was called, is different than the current thread
 * <li>from the current thread with intermediate native frames
 * <li>no JVMTI events will be generated by the function <code>PopFrame()</code>
 *
 *   Fixed according to the 4528173 bug:
 *     for now the test checks that a native frame may not be poped.
 */
public class popframe004 {
    public static final int PASSED = 0;
    public static final int FAILED = 2;
    static final int JCK_STATUS_BASE = 95;

    static boolean DEBUG_MODE = false;
    static volatile boolean popFdone = false;
    static volatile boolean popF2done = false;
    static volatile int totRes = PASSED;
    private PrintStream out;
    private popFrameCls popFrameClsThr;
    static Object barrier = new Object(); // for suspending a child thread

    static {
        try {
            System.loadLibrary("popframe004");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Could not load popframe004 library");
            System.err.println("java.library.path:" +
                System.getProperty("java.library.path"));
            throw e;
        }
    }

    native void nativeMeth(int vrb, popFrameCls popFrameClsThr);
    native static int doPopFrame(int t_case, Thread popFrameClsThr);
    native static int getResult();

    public static void main(String[] argv) {
        argv = nsk.share.jvmti.JVMTITest.commonInit(argv);

        System.exit(run(argv, System.out) + JCK_STATUS_BASE);
    }

    public static int run(String argv[], PrintStream out) {
        return new popframe004().runIt(argv, out);
    }

    private int runIt(String argv[], PrintStream out) {
        int retValue = 0;

        this.out = out;
        for (int i = 0; i < argv.length; i++) {
            if (argv[i].equals("-v")) // verbose mode
                DEBUG_MODE = true;
        }

        PipedInputStream pipeIn = new PipedInputStream();
        popFrameClsThr = new popFrameCls(pipeIn);
        synchronized (barrier) { // force a child thread to pause
            popFrameClsThr.start(); // start a separate thread
// wait until the thread will enter into a necessary method
            try {
                int _byte = pipeIn.read();
            } catch (IOException e) {
                out.println("TEST FAILED: reading from a pipe: caught " + e);
                return FAILED;
            }
// pop the frame
            if (popFrameClsThr.isAlive()) {
                if (DEBUG_MODE) {
                    out.println("Going to pop the frame...");
                    retValue=doPopFrame(1, popFrameClsThr);
                } else
                    retValue=doPopFrame(0, popFrameClsThr);
                popFdone = true;
                if (retValue != PASSED)
                    return FAILED;
            }
            else {
                out.println("TEST FAILURE: thread with the method of " +
                    "the popped frame is dead");
                return FAILED;
            }
        }

        try {
            if (popFrameClsThr.isAlive())
                popFrameClsThr.join(2000);
        } catch (InterruptedException e) {
            out.println("TEST INCOMPLETE: caught " + e);
            totRes = FAILED;
        }
        if (popFrameClsThr.isAlive()) {
            out.println("TEST FAILED: thread with the method of " +
                "the popped frame is still alive");
            totRes = FAILED;
        }

        if (DEBUG_MODE) {
            out.println("Going to pop the native frame...");
            totRes=doPopFrame(5, Thread.currentThread());
        } else
            totRes=doPopFrame(4, Thread.currentThread());

        if (totRes != PASSED)
            return FAILED;
        else return getResult();
    }

    class popFrameCls extends Thread {
        private PipedInputStream pipeIn;
        private PipedOutputStream pipeOut;

        popFrameCls(PipedInputStream pipeIn) {
            this.pipeIn = pipeIn;
            try {
                pipeOut = new PipedOutputStream(pipeIn);
            } catch (IOException e) {
                out.println("popFrameCls (" + this +
                "): creating a pipe: caught " + e);
                return;
            }
        }

        public void run() {
            if (DEBUG_MODE)
                nativeMeth(1, popFrameClsThr);
            else nativeMeth(0, popFrameClsThr);
            if (DEBUG_MODE)
                out.println("popFrameCls (" + this +
                    "): exiting...");
        }

        public void activeMethod() {
            int retVal = FAILED;
            boolean compl = true;

            if (popframe004.popFdone) { // popping has been done
                if (DEBUG_MODE)
                    out.println("popFrameCls (" + this +
                        "): enter activeMethod() after popping");
// test case #2: popping from the current thread
                if (!popframe004.popF2done) {
                    popframe004.popF2done = true;
                    if (DEBUG_MODE) {
                        out.println("popFrameCls (" + this +
                        "): going to pop a frame from the current thread...");
                        retVal = doPopFrame(3, popFrameClsThr);
                    } else
                        retVal = doPopFrame(2, popFrameClsThr);
                    if (retVal != PASSED)
                        popframe004.totRes = FAILED;
                }
                if (DEBUG_MODE)
                    out.println("popFrameCls (" + this +
                        "): leaving activeMethod()...");
                return;
            }
            try {
                pipeOut.write(123); // notify the main thread
                if (DEBUG_MODE)
                    out.println("popFrameCls (" + this +
                        "): inside activeMethod()");
// pause here until the main thread suspends us
                synchronized (popframe004.barrier) {
                    while (true) {
                        if (DEBUG_MODE)
                            out.println("popFrameCls (" + this +
                                "): looping...");
                        if (popframe004.popFdone) { // popping has been done
                            // popping has not to be done due to native frame
                            if (DEBUG_MODE)
                                out.println("popFrameCls (" + this +
                                    "): exiting activeMethod()...");
                            compl = false;
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                out.println("FAILURE: popFrameCls (" + this +
                "): caught " + e);
                popframe004.totRes = FAILED;
                compl = false;
            } finally {
                if (compl) {
                    out.println("TEST FAILED: finally block was executed after popping");
                    popframe004.totRes = FAILED;
                }
            }
        }
    }
}
