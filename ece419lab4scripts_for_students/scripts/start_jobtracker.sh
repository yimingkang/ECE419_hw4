cd ../bin/ && java -Djava.util.logging.SimpleFormatter.format='%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %2$s %5$s%6$s%n' -classpath lib/zookeeper-3.3.2.jar:lib/log4j-1.2.15.jar:. JobTracker "$1":"$2"
