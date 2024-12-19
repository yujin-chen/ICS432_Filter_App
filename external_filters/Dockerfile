FROM henricasanova/ics432_on_linux

# ics432 user
RUN useradd -ms /bin/bash ics432
RUN adduser ics432 sudo
RUN echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers

USER ics432
WORKDIR /home/ics432
ENV CC="gcc"

# c_filters code
COPY --chown=ics432 c_filters /home/ics432/c_filters
RUN cd c_filters && mkdir build && cd build && cmake .. && make && sudo make install 

# input / output dirs
RUN mkdir /tmp/input/
RUN mkdir /tmp/output/
