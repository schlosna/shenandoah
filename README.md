Minimal Complete Verifiable Example (MCVE) repro for [https://bugs.openjdk.java.net/browse/JDK-8216364](https://bugs.openjdk.java.net/browse/JDK-8216364) OpenJDK 11 crash with Shenandoah GC on CentOS 7.

In the interest of hardening Shenandoah GC, I wanted to see if folks here are interested in fixing this as we were able to create a minimal repro that quickly triggers the JIT crash consistently. Note that both the original bug sighting and repro are using LMAX disruptor 3.4.2 which relies heavily on sun.misc.unsafe and had [filed an issue there](https://github.com/LMAX-Exchange/disruptor/issues/251). Dropping in the [VarHandles based disruptor version](https://github.com/LMAX-Exchange/disruptor/tree/jdk9-varhandles) does not trigger the crash.

Crash:
```
Current thread (0x00007f2bb41fb800):  JavaThread "C2 CompilerThread0" daemon [_thread_in_native, id=94124, stack(0x00007f2bb88f5000,0x00007f2bb89f6000)]

Current CompileTask:
C2:    245  301 %     4       com.lmax.disruptor.MultiProducerSequencer::initialiseAvailableBuffer @ 8 (31 bytes)
```

## Steps to reproduce

1. Build 
```
./gradlew distTar
```

2. Run
```
tar xvf ./build/distributions/shenandoah.tar
SHENANDOAH_OPTS="-XX:+UseShenandoahGC" ./shenandoah/bin/shenandoah
```

3. Receive crash output:
```
#
# A fatal error has been detected by the Java Runtime Environment:
#
#  SIGSEGV (0xb) at pc=0x00007f2bbcaf43cd, pid=94107, tid=94124
#
# JRE version: OpenJDK Runtime Environment (11.0.1+13) (build 11.0.1+13-LTS)
# Java VM: OpenJDK 64-Bit Server VM (11.0.1+13-LTS, mixed mode, sharing, tiered, compressed oops, shenandoah gc, linux-amd64)
# Problematic frame:
# V  [libjvm.so+0xcac3cd]
#
# Core dump will be written. Default location: Core dumps may be processed with " /usr/lib/systemd/systemd-coredump %p %u %g %s %t %c %e" (or dumping to /home/davids/shenandoah/core.94107)
#
# An error report file with more information is saved as:
# /home/davids/shenandoah/hs_err_pid94107.log
#
# Compiler replay data is saved as:
# /home/davids/shenandoah/replay_pid94107.log
#
# If you would like to submit a bug report, please visit:
#   http://bugreport.java.com/bugreport/crash.jsp
#

---------------  T H R E A D  ---------------

Current thread (0x00007f2bb41fb800):  JavaThread "C2 CompilerThread0" daemon [_thread_in_native, id=94124, stack(0x00007f2bb88f5000,0x00007f2bb89f6000)]


Current CompileTask:
C2:    245  301 %     4       com.lmax.disruptor.MultiProducerSequencer::initialiseAvailableBuffer @ 8 (31 bytes)

Stack: [0x00007f2bb88f5000,0x00007f2bb89f6000],  sp=0x00007f2bb89f0890,  free space=1006k
Native frames: (J=compiled Java code, A=aot compiled Java code, j=interpreted, Vv=VM code, C=native code)
V  [libjvm.so+0xcac3cd]
V  [libjvm.so+0xdac520]
V  [libjvm.so+0xdbfc47]
V  [libjvm.so+0xaf4de0]
V  [libjvm.so+0xdae9f4]
V  [libjvm.so+0x6b6616]
V  [libjvm.so+0x6b774c]
V  [libjvm.so+0x5d6496]
V  [libjvm.so+0x6c02cb]
V  [libjvm.so+0x6c1d78]
V  [libjvm.so+0xed4472]
V  [libjvm.so+0xed47d8]
V  [libjvm.so+0xc58482]
```

See [doc/hs_err_pid94107.log](doc/hs_err_pid94107.log) and [doc/replay_pid94107.log](doc/replay_pid94107.log) for full crash log output.


gdb backtrace:
```
(gdb) bt
#0  0x00007fd2d0654207 in __GI_raise (sig=sig@entry=6) at ../nptl/sysdeps/unix/sysv/linux/raise.c:55
#1  0x00007fd2d06558f8 in __GI_abort () at abort.c:90
#2  0x00007fd2cfc75c99 in os::abort (dump_core=<optimized out>, siginfo=<optimized out>, context=<optimized out>) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/os/linux/os_linux.cpp:1411
#3  0x00007fd2cff5f559 in VMError::report_and_die (id=<optimized out>, message=message@entry=0x0, detail_fmt=detail_fmt@entry=0x7fd2d0013b60 "%s", detail_args=detail_args@entry=0x7fd2b0481020, thread=thread@entry=0x7fd2c81fb800,
    pc=pc@entry=
    0x7fd2cfcd23cd <NodeHash::hash_delete(Node const*)+13> "H\213\006H\211\363\377PX\211\302\061\300\205\322tJA\213|$\bM\213D$\030\215w\377!\362\211\321A\211\321I\215\f\310A\203\311\001H\213\071H\205\377t&H9\373u\016\353'\017\037\200",
    siginfo=siginfo@entry=0x7fd2b0481430, context=context@entry=0x7fd2b0481300, filename=filename@entry=0x0, lineno=lineno@entry=0, size=size@entry=0)
    at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/utilities/vmError.cpp:1541
#4  0x00007fd2cff5ff53 in VMError::report_and_die (thread=thread@entry=0x7fd2c81fb800, sig=sig@entry=11,
    pc=pc@entry=0x7fd2cfcd23cd <NodeHash::hash_delete(Node const*)+13> "H\213\006H\211\363\377PX\211\302\061\300\205\322tJA\213|$\bM\213D$\030\215w\377!\362\211\321A\211\321I\215\f\310A\203\311\001H\213\071H\205\377t&H9\373u\016\353'\017\037\200", siginfo=siginfo@entry=0x7fd2b0481430, context=context@entry=0x7fd2b0481300, detail_fmt=detail_fmt@entry=0x7fd2d0013b60 "%s")
    at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/utilities/vmError.cpp:1241
#5  0x00007fd2cff5ffa1 in VMError::report_and_die (thread=thread@entry=0x7fd2c81fb800, sig=sig@entry=11,
    pc=pc@entry=0x7fd2cfcd23cd <NodeHash::hash_delete(Node const*)+13> "H\213\006H\211\363\377PX\211\302\061\300\205\322tJA\213|$\bM\213D$\030\215w\377!\362\211\321A\211\321I\215\f\310A\203\311\001H\213\071H\205\377t&H9\373u\016\353'\017\037\200", siginfo=siginfo@entry=0x7fd2b0481430, context=context@entry=0x7fd2b0481300) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/utilities/vmError.cpp:1247
#6  0x00007fd2cfc8070a in JVM_handle_linux_signal (sig=11, info=0x7fd2b0481430, ucVoid=0x7fd2b0481300, abort_if_unrecognized=<optimized out>)
    at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/os_cpu/linux_x86/os_linux_x86.cpp:620
#7  0x00007fd2cfc743a8 in signalHandler (sig=11, info=0x7fd2b0481430, uc=0x7fd2b0481300) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/os/linux/os_linux.cpp:4497
#8  <signal handler called>
#9  NodeHash::hash_delete (this=this@entry=0x7fd2b04835b8, n=n@entry=0x0) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/opto/phaseX.cpp:235
#10 0x00007fd2cfdd2520 in hash_delete (n=n@entry=0x0, this=0x7fd2b0482c30) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/opto/phaseX.hpp:375
#11 rehash_node_delayed (n=n@entry=0x0, this=0x7fd2b0482c30) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/opto/phaseX.hpp:520
#12 PhaseIterGVN::replace_input_of (this=0x7fd2b0482c30, n=n@entry=0x0, in=0x7fd2800a51e8, i=0) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/opto/phaseX.hpp:530
#13 0x00007fd2cfde5c47 in ShenandoahWriteBarrierNode::pin_and_expand (phase=phase@entry=0x7fd2b0481fe0) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/gc/shenandoah/c2/shenandoahSupport.cpp:3053
#14 0x00007fd2cfb1ade0 in PhaseIdealLoop::build_and_optimize (this=this@entry=0x7fd2b0481fe0, mode=mode@entry=LoopOptsShenandoahExpand)
    at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/opto/loopnode.cpp:2898
#15 0x00007fd2cfdd49f4 in PhaseIdealLoop (mode=LoopOptsShenandoahExpand, igvn=..., this=0x7fd2b0481fe0) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/opto/loopnode.hpp:945
#16 ShenandoahWriteBarrierNode::expand (C=0x7fd2b0484dc0, igvn=..., loop_opts_cnt=@0x7fd2b0482ac0: 37) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/gc/shenandoah/c2/shenandoahSupport.cpp:545
#17 0x00007fd2cf6dc616 in Compile::Optimize (this=0x7fd2b0484dc0) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/opto/compile.cpp:2416
#18 0x00007fd2cf6dd74c in Compile::Compile (this=0x7fd2b0484dc0, ci_env=<optimized out>, compiler=0x7fd2c81fafa0, target=<optimized out>, osr_bci=<optimized out>, subsume_loads=<optimized out>, do_escape_analysis=true,
    eliminate_boxing=true, directive=0x7fd2c81e16d0) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/opto/compile.cpp:882
#19 0x00007fd2cf5fc496 in C2Compiler::compile_method (this=0x7fd2c81fafa0, env=0x7fd2b0485a20, target=0x7fd2781809e0, entry_bci=8, directive=0x7fd2c81e16d0)
    at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/opto/c2compiler.cpp:109
#20 0x00007fd2cf6e62cb in CompileBroker::invoke_compiler_on_method (task=task@entry=0x7fd2c82037c0) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/compiler/compileBroker.cpp:2112
#21 0x00007fd2cf6e7d78 in CompileBroker::compiler_thread_loop () at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/compiler/compileBroker.cpp:1808
#22 0x00007fd2cfefa472 in JavaThread::thread_main_inner (this=this@entry=0x7fd2c81fb800) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/runtime/thread.cpp:1752
#23 0x00007fd2cfefa7d8 in JavaThread::run (this=0x7fd2c81fb800) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/share/runtime/thread.cpp:1732
#24 0x00007fd2cfc7e482 in thread_native_entry (thread=0x7fd2c81fb800) at /usr/src/debug/java-11-openjdk-11.0.1.13-3.el7_6.x86_64/openjdk/src/hotspot/os/linux/os_linux.cpp:698
#25 0x00007fd2d0e07dd5 in start_thread (arg=0x7fd2b0486700) at pthread_create.c:307
#26 0x00007fd2d071bead in clone () at ../sysdeps/unix/sysv/linux/x86_64/clone.S:111
```
