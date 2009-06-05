;   thread library for clojure
;   dfw407so@dd.bib.de

;;; ------------
;;; thread stuff
;;; ------------

(ns hoeck.thread
  (:use hoeck.library
        clojure.contrib.pprint)
  (:import (java.util.concurrent
            ThreadPoolExecutor
            Executors 
            ExecutorService
            ScheduledThreadPoolExecutor
            TimeUnit)))

(defn get-timeunit [key]
  (if-let [tu ({:milli (. TimeUnit MILLISECONDS)
                :micro (. TimeUnit MICROSECONDS)
                :nano  (. TimeUnit NANOSECONDS)
                :sec   (. TimeUnit SECONDS)}
               key)]
    tu
    (throw (Exception. (str "unknown timeunit: " key )))))


;;; fn's are now runnable *joy*
;;;(defmacro runnable [f]
;;;  `(proxy [java.lang.Runnable] [] (~'run [] (~f))))

;;; 
(defn call-repeatedly
  "Call a function f every time time-units.
  Return a function which kills the threadpool.
  Uses ScheduledThreadPoolExecutor."
  ([f time] (call-repeatedly f time :micro))
  ([f time time-unit]
   (let [s (new ScheduledThreadPoolExecutor 1)]
     (. s (scheduleAtFixedRate f 0 time (get-timeunit time-unit)))
     (fn [] (. s (shutdownNow))))))

(defn get-current-threads
  "Return all currently accesible threads in an array."
  []
  (let [threads (make-array Thread (Thread/activeCount))]
    (Thread/enumerate threads)
    threads))

(defn get-thread
  "Return the java.lang.Thread matching thread-id.
  Thread-id may be a number - matching on a threadId or
  a string, symbol, keyword matching partially a threadsName.
  Without thread-id, return the current thread."
  ([] (Thread/currentThread))
  ([thread-id]
     (if (instance? Thread thread-id)
       thread-id
       (let [threads (get-current-threads)]
         (first (filter (cond (number? thread-id)
                              #(= thread-id (.getId %))
                              (or (symbol? thread-id) (keyword? thread-id) (string? thread-id))
                              (let [name-pattern (java.util.regex.Pattern/compile 
                                                  (str ".*" (cond (string? thread-id)
                                                                  thread-id
                                                                  :else
                                                                  (symbol-name thread-id))
                                                       ".*"))]
                                #(re-find name-pattern (.getName %))))
                        threads))))))


;;; todo: use cl-format!
;(defn ps-prn
;  "Display information about currently accessible Threads running on the vm."
;  []
;  (let [p #(print-al %1 %2 :left)
;        layout '(3 16 13 10 8 10 8)]
;    (apply println ";;" (map #(p %1 %2) '(id name state priority alive interupted daemon) layout))
;    (count (doall
;     (map (fn [t] (println ";;"
;                   (p (.getId t) (nth layout 0)) 
;                   (p (.getName t) (nth layout 1))
;                   (p (.getState t) (nth layout 2))
;                   (p (.getPriority t) (nth layout 3))
;                   (p (.isAlive t) (nth layout 4))
;                   (p (.isInterrupted t) (nth layout 5))
;                   (p (.isDaemon t) (nth layout 6))))
;          (get-current-threads))))))

(defn ps-list
  "list all known threads by name"
  []
  (map #(.getName %) (get-current-threads)))

(def ps ps-list)

(defn interrupt
  "set interrupt state of thread thread-id or the current thread to true"
  ([] (.interrupt (get-thread)))
  ([thread-id]
     (if-let [t (get-thread thread-id)] (do (.interrupt t) (.isInterrupted t)) nil)))

(defn interrupted?
  "test wether the current thread is interrupted."
  []
  (.isInterrupted (Thread/currentThread)))

(defn thread-sleep
  "Put this thread to sleep for timeunit amount of time
  return true if thread sleep is complete, return false if thread
  was interrupted before or gets interrupted while sleeping."
  ([milliseconds] (thread-sleep (get-timeunit :milli) milliseconds))
  ([timeunit amount]
     (and (not (interrupted?))
          (try (.sleep timeunit amount)
               true
               (catch java.lang.InterruptedException e false)))))                

(defn get-stack-trace
  [thread-id]
  (.getStackTrace (get-thread thread-id)))

(defn background
  "Run function f in a Thread in the background and ignore its result."
  ([f] (background f (gensym "background-")))
  ([f name] (.start (new Thread f (str name)))))

(defn background-repeatedly
  "call function f repeatedly. take time milliseconds sleep between 2 calls to f."
  ([f time-in-milliseconds] (background-repeatedly f time-in-milliseconds (gensym "background-repeatedly-")))
  ([f time-in-milliseconds name]
     (background (fn [] (loop []
                          (f)
                          (if (thread-sleep time-in-milliseconds) (recur))))
                 name)))


(defn background-periodically ;; the classical game-loop
  "Call a function f (with side-effects) exactly n times a second, regardless how long the 
  function takes to execute (must be < (/ 1 times-per-second))."
  ([f times-per-second] (background-periodically f times-per-second (gensym "background-periodically-")))
  ([f times-per-second name]
     (let [period-nanos (/ (.toNanos (get-timeunit :sec) 1) times-per-second)
           tu (get-timeunit :nano)]
       (background (fn []
                     (let [start (System/nanoTime)]
                       (f)
                       (if (thread-sleep tu (or (positive? (- period-nanos (- (System/nanoTime) start))) 0)) (recur))))
                   name))))

(defn in-background
  "call function in background and return a future."
  [f]
  (.submit (.newSingleThreadExecutor Executors) #^java.util.concurrent.Callable f))


 

