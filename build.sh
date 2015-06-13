old="$PWD"

command -v mvn >/dev/null 2>&1 || (curl -o /opt/maven.tar.gz http://psg.mtu.edu/pub/apache/maven/maven-3/3.2.5/binaries/apache-maven-3.2.5-bin.tar.gz; cd /opt && tar xfz maven.tar.gz && ln -s /opt/apache-maven-*/bin/mvn /usr/local/bin/mvn)

command -v javac >/dev/null 2>&1 || yum install -y java-1.7.0-openjdk-devel

cd "$old"
mvn clean package


