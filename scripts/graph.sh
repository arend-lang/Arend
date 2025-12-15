echo "Run from the root directory (/home/.../Arend)"
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

if [ -z "$3" ]; then
    read -p "Enter the (new) arend-lib version, or leave it empty to use the version from the YAML file: " arg3
else
    arg3="$3"
fi

read -p "Enter the names of Arend classes to include in the graph, or leave blank to generate a graph of all classes: " -a extra_args

gradleCmd=(./gradlew generateArendLibGraph
    -PpathToArendLib="$arg1"
    -PpathToArendLibInArendSite="$arg2"
)

if [ -n "$arg3" ]; then
    gradleCmd+=("-PversionArendLib=$arg3")
fi

if [ "${#extra_args[@]}" -gt 0 ]; then
    classes_arg=$(IFS=','; echo "${extra_args[*]}")
    gradleCmd+=("-Pclasses=$classes_arg")
fi

"${gradleCmd[@]}"