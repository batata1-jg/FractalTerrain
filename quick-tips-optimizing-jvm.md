# Quick Tips for Fast Code on the JVM

I was talking to a coworker recently about general techniques that almost always form the core of any effort to write very fast, down-to-the-metal hot path code on the JVM, and they pointed out that there really isn't a particularly good place to go for this information.  It occurred to me that, really, I had more or less picked up all of it by word of mouth and experience, and there just aren't any good reference sources on the topic.  So… here's my word of mouth.

This is by no means a comprehensive gist.  It's also important to understand that the techniques that I outline in here are not 100% absolute either.  Performance on the JVM is an incredibly complicated subject, and while there are rules that almost always hold true, the "almost" remains very salient.  Also, for many or even most applications, there will be other techniques that I'm not mentioning which will have a greater impact.  JMH, Java Flight Recorder, and a good profiler are your very best friend!  Measure, measure, measure.  Then measure again.

As a quick disclaimer…  I know a lot about optimizing JVM performance, but I don't consider myself an expert.  I sincerely apologize if any of the information in this article is wrong or misleading; I'll try to correct as quickly as possible if someone more knowledgeable is willing to set me straight.

## Defining the Hot Path

Here's a general one that literally always holds: know where your hot path is, and delineate it *very* clearly.  By "hot path", I mean anything that you would reasonably expect to happen hundreds of thousands, or millions of times per second under load (or more!).  As a general rule of thumb, about 80-90% of an application's performance issues are going to stem solely from issues in the hot path, since that's precisely where the application is going to spend 80-90% of its time.  Being very sure of where this is (and where it isn't!) is incredibly important.

In broader terms, I like to think of an application as divided by a horizontal line into two regions.  Above the line, we have the orchestration and business logic of the application.  This region can be slow, because it doesn't evaluate on a "per item" basis (whatever "item" means for your application).  We expect the code in this region to run only a few dozen times per second at most, and possibly far less.  This code should be highly abstracted, composable, lawful, optimized for reasoning and maintainability, *not* for speed.

Below the "line", we have the hot path, and code which will run on a "per item" or nearly per item basis.  This code cannot be polymorphic (except within certain bounds; more on this later), and it has to rely heavily on constructs which the JVM understands well and which ultimately JIT compile into tight, pipeline-friendly assembly.  Maintainability is an afterthought in this code.  Instead, all you care about is wringing out the last few microseconds from the turnip.  Even the principles of functional programming should be considered open for debate (or outright ignored!), so long as you can maintain the lawfulness and composability of the region above the line.

Your goal should always be to push as much of the logic of your application *above* the line.  Try to think about problems in terms of orchestrating a complicated Rube Goldberg machine with the logic above the line, which is then run *really* fast without any decision making by the logic below the line.  Making all your decisions, all your compositions, and nearly all your *stuff that you want to write tests for* above the line helps tremendously with long-term application maintainability, since this code is allowed to be slow and can thus take advantage of richer abstractions.

One common trick for finding this line is the concept of a "chunk boundary".  This concept comes from stream programming, and it's the idea that you should compose and manipulate streams only at the "chunk" level, which usually means something on the order of "once every 10000 elements".  Not every application decomposes naturally into stream-ish things, but a *lot* of them do, and it's a helpful model to use when thinking about this problem.  Just in general, if you work around the chunk boundary, it means that you're not going to have any logical branching *within* a chunk, which means in turn that you cannot make any real control flow decisions based on *individual* elements, only based on linearly-determinable statistics on entire chunks.

One of the nice things about streaming frameworks (Akka Streams, fs2, Monix, etc) is that they strongly encourage and even *force* this paradigm, which makes it easy to do the right thing regarding your application's performance architecture.

## Doing Things in the Hot Path

Obviously, you're going to need *some* logic that evaluates in the hot path.  Knowing exactly what you can and cannot do a hundred million times per second on the JVM is vital to gerrymandering your logic appropriately, and also just generally avoiding the use of avoidable constructs that make things unexpectedly.  This section contains some tips which are, at least in my experience, the most common and most impactful techniques in this category.

### Don't. Allocate.

The `new` operator on the JVM is actually a lot faster than you might think, if you understand everything that it really does.  A remarkably large percentage of the man-millennium of research that has gone into optimizing HotSpot has specifically targeted allocations and memory management.  However, despite the fact that `new` is very good at doing its job, its job is still inherently very very expensive.

Even extremely short-lived objects that are created, read, and then immediately discarded within a single loop cycle are an order of magnitude more expensive than manually encoding the escape-analysis and keeping things in the land of primitives.  Always remember that *primitives* on the JVM (`byte`, `short`, `int`, `long`, `float`, `double`, `boolean`, and arguably `String`) are extremely magical and vastly, *hilariously* lighter-weight than any value that has a method dispatch table.  Every allocation that you perform is not just the expense of round-tripping through main memory, incrementing heap pointers, cloning dispatch tables, and generally just giving you a pointer, it is also the cost of later deallocation when the instance has gone out of scope, the indirect cost of the GC figuring out *when* the instance has gone out of scope, the indirect cost of heap fragmentation and inevitable reorganization/graduation that is hastened by more and more allocation and deallocation cycles.  It adds up so incredibly fast.

And that's before we get to the fact that object allocation always imposes multiple round-trips to main memory, which is extremely expensive relative to registers and the cache, not to mention whole blocks of instructions that are hard (or impossible) for CPUs to pipeline-optimize due to the combination of error checking and concurrency.  It's just horrible.

Don't. Allocate.  If you absolutely must use objects within the hot path, try to make sure they all get pre-allocated and mutated on a per-element basis.  While this sounds like functional sacrilege, it is a necessary price to pay, and you're hiding all of this within higher level (pure!) abstractions anyway, so it shouldn't hurt (much).

#### Boxing

Be aware of the impacts of type erasure on your quest to eliminate allocations.  What I mean by that is the fact that the hard distinction between primitive types (good!) and reference types (bad!) on the JVM often results in allocations that are entirely unexpected and often very avoidable.  As an example:

```scala
def first[A](left: A, right: A): A = left

first(12, 42)   // => 12
```

Not a particularly interesting function, but ignore that for now.  The *problem* here is the fact that there will be *two* allocations per call to `first`, even when we're instantiating `A` with a primitive type (`Int`).  This is because the `first` function is compiled to a bytecode signature something like the following: `(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;`.  This makes sense, because `A` *could* be instantiated with a reference type, and the Scala compiler doesn't know whether or not this is the case when it compiles `first`.  While it is always possible to lift a primitive type into a reference type (by allocating a boxing object), it isn't always possible to go in the other direction, so Scala errs on the side of correctness.

You can avoid this problem in one of two ways: either use the unreliable and mysterious `@specialized` annotation on `A`, or do the equivalent manually.  I prefer the manual approach:

```scala
def firstInt(left: Int, right: Int): Int = left

firstInt(12, 42)
```

No allocations there!  And while you might end up creating a bunch of different versions of `first`, the boilerplate will more than pay for itself in the resulting performance gains.

### Keep Things Monomorphic or Bimorphic

These terms are actually pretty weird, and I honestly don't know of any other virtual machines which optimize call-sites in quite exactly the way that HotSpot does.  So let's take a second to dive into that...

HotSpot considers something a "call-site" if it is a fragment of bytecode that starts with `invoke`.  Note that call-sites are not separated by instance (i.e. two different instances of `List` have the same call-sites within their implementation of the `map` function, since there is only one chunk of bytecode), nor are they separated by type (e.g. `List` and `Vector` actually share a `map` implementation, and thus in turn share the same set of call-sites *within* `map`, since they inherit it from the same place).  Every call-site has its own *polymorphic inline cache*, or PIC.  Understanding and optimizing around the PIC is one of the most important things you can do to improve HotSpot's performance.

The PIC works by watching the *runtime* type of the *dispatch receiver* for its particular call-site.  To be clear, the dispatch receiver is the instance on which a method is being called.  For example:

```scala
foo.bar(baz)
```

The dispatch receiver of the `bar` method call here is `foo`, while `baz` is a parameter.  The PIC for this call-site will be looking at the *runtime* type of `foo`, every time it evaluates the corresponding `invoke` instruction.  As an example:

```scala
def bippy(stuff: AnyRef) = stuff.hashCode    // stuff is the receiver

bippy(List(42))
bippy("fubar")
bippy(new AnyRef)
```

The static type of `stuff` is `AnyRef`, but the PIC doesn't really care about that (unless the static type happens to be `final`, in which case it cares a lot).  What it cares about are the runtime types that it *observes* passing through the `hashCode` call-site.  In this case, it will observe three types: `scala.List`, `java.lang.String`, and `java.lang.Object`.  This is really important to understand: the *possible* polymorphism of a method dispatch doesn't matter, it's only the *actual* observed polymorphism.  So if you write a super-general function that does some complicated polymorphic dispatch, but you only ever call that function on a single type, then all of the call-sites within that function will only ever see a single runtime type, and they will be considered to be *monomorphic*.

Monomorphic call-sites are awesome.  Once a call-site has been invoked a number of times that exceeds the threshold (which defaults to 10000 invocations, IIRC), the PIC will check its metrics and make a decision about the *actual* polymorphism of the call-site.  If the call-site has been observed to be monomorphic by the PIC, then HotSpot will emit bytecode which looks something like the following pseudocode:

```scala
if (receiver.getClass eq Expectation)
  goto Expectation.methodName(parameters)
else
  throw new PanicAndDeoptimizeEverythingException(receiver)
```

Basically, HotSpot is going to do a very quick check to make sure its assumptions still hold, and if they do, it will unconditionally jump *directly* to the chunk of bytecode (or more likely by this point, assembly) which implements the method for that particular runtime type.  This is insanely fast, and modern CPUs eat it up.  If the check fails though, then HotSpot just assumes that this is a really weird call-site (since it was monomorphic for 10000+ dispatches, and *only now* is demonstrating polymorphism), and it will undo all of its optimizations around this call-site and never try again.

Bimorphic call-sites are just like monomorphic call-sites, except they have *two* observed runtime types.  In this case, HotSpot will still emit code which looks like the above, it will just have a second branch for the other type.  This is still something that is extremely fast on modern CPUs, though obviously it's not quite as good since there's an extra conditional in play.

As a general rule, anything more than two observed runtime types is bad bad *BAD* news.  There are some very specialized things that HotSpot can sometimes do if you only have three runtime types (i.e. "polymorphic") and not more (i.e. "megamorphic"), but honestly they don't make that much of a difference most of the time.  To make matters worse, the impact of this *specific* optimization goes far beyond just CPU pipelining and emitting `goto` instructions.

The thing is that most higher-level optimizations in HotSpot require some understanding of concrete control flow.  It's impossible to get a static understanding of control flow in the face of actual, realized polymorphism, and so the JVM relies on the PIC to figure things out.  This is generally a very good thing, and what happens in an ideal case is the PIC will discover a call-site is monomorphic, and this instrumentation will then inform *almost everything* else that the HotSpot JIT is trying to do.  Very powerful optimizations such as inlining, escape analysis, type-directed analysis, register- and stack-allocated values, and much more are all gated behind the PIC.  If the PIC decides a call-site is mono- or bimorphic, then all these other optimizations can be brought to bear.  If not, then the call-site is just horrible and HotSpot will not even try to do very much with it, which *in turn* affects how much HotSpot can do with the surrounding code both in the calling method and in the called method (since HotSpot now understands less about where control flow is coming from).

**Edit:** A prior version of this gist claimed that HotSpot drops into interpreted mode for all megamorphic call-sites.  [This is incorrect.](https://shipilev.net/jvm-anatomy-park/16-megamorphic-virtual-calls/)

The solution here is just to avoid polymorphism in the hot path unless you can be *100%* certain beyond any shadow of a doubt that the runtime dispatch receiver types will always be either a single type or one of two types.  This of course means that you cannot use abstractions of any kind whatsoever in the hot path, since abstraction by definition means code reuse, and code reuse requires polymorphism, and polymorphism gets us stuck in this whole mud hole.  So you're going to end up writing a *lot* of very manual, very uncomposable boilerplate.  This is deeply unfortunate, but it's the price you pay for having a hot path.

## Conclusion

There are obviously a lot of other things you can do to improve the performance of your code on the JVM.  This is just a couple of really big, really unambiguous ones that almost always apply in a given codebase.  The only other tip I can think of which is anywhere near as important as the above would be tuning your GC configuration very carefully, since that has a bigger impact on your performance than you probably believe.  There's a lot of good documentation (and books!) on this subject, though.
