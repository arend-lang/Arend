if [ -z "$1" ]; then
    read -p "Specify the path to the root of the arend-lib source code (/home/.../Arend/arend-lib): " arg1
else
    arg1="$1"
fi

if [ -z "$2" ]; then
    read -p "Specify the path to the arend-lib root in the arend-site repository (/home/.../site/src/arend-lib): " arg2
else
    arg2="$2"
fi

DEFAULT_UPDATE_SCHEME=false
if [ -z "$3" ]; then
    read -p "Should the global theme color scheme be updated (true/false)? This parameter is optional; if not specified, it defaults to false: " arg3
    arg3="${arg3:-$DEFAULT_UPDATE_SCHEME}"
else
    arg3="$3"
fi

if [ -z "$4" ]; then
    read -p "Enter the (new) arend-lib version, or leave it empty to use the version from the YAML file: " arg4
else
    arg4="$4"
fi

read -p "Enter the names of Arend classes to include in the graph, or leave blank to generate a graph of all classes: " -a extra_args

gradleCmd=(./gradlew generateArendLib
    -PpathToArendLib="$arg1"
    -PpathToArendLibInArendSite="$arg2"
    -PupdateColorScheme="$arg3"
)

if [ -n "$arg4" ]; then
    gradleCmd+=("-PversionArendLib=$arg4")
fi

if [ "${#extra_args[@]}" -gt 0 ]; then
    classes_arg=$(IFS=','; echo "${extra_args[*]}")
    gradleCmd+=("-Pclasses=$classes_arg")
fi

cd ..
"${gradleCmd[@]}"

#generateArendLibHtml
# -PpathToArendLib=
# /home/aluchinin/Arend/arend-lib
# -PpathToArendLibInArendSite=
# /home/aluchinin/site/src/arend-lib

# -Pclasses=BaseSet