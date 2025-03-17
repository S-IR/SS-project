cmake_minimum_required(VERSION 3.22.1)
project("myapplication")

add_library( # Creates libnative-lib.so
        native-lib
        SHARED
        native-lib.cpp )

find_library( log-lib log )
target_link_libraries( native-lib ${log-lib} )