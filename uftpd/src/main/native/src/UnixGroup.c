#include <jni.h> 
#include <stdio.h> /* for NULL */
#include <grp.h>
#include <sys/types.h>
#include <unistd.h> 
#include <eu_unicore_uftp_server_unix_UnixGroup.h>

/*
 * author: Valentina Huber, Forschungszentrum Juelich GmbH
 */

/* Fills in a Group object's fields with information from a group structure. */
static void populateObject(JNIEnv *jniEnv, jobject objectInstance, struct group *grp)
{
  static jclass clazz;
  static jfieldID gidID;
  static jfieldID nameID;
  static jfieldID membersID;

  static jobject membersVector;
  jmethodID midCtor;
  jmethodID midAddElement;
  
  jstring nameString;
  jstring memberString;

  int i;  

  /* Find the object's class */
  clazz = (*jniEnv)->GetObjectClass(jniEnv, objectInstance);

  /* Look up each of the fields we're interested in */
  gidID = (*jniEnv)->GetFieldID(jniEnv, clazz, "gid", "I");
  nameID = (*jniEnv)->GetFieldID(jniEnv, clazz, "name", "Ljava/lang/String;");
  membersID = (*jniEnv)->GetFieldID(jniEnv, clazz, "members", "Ljava/util/List;");

  /* Convert C strings to Java strings */
  nameString = (*jniEnv)->NewStringUTF(jniEnv, grp->gr_name);

  /* Set the fields in the UnixGroup object */
  (*jniEnv)->SetIntField(jniEnv, objectInstance, gidID, grp->gr_gid);
  (*jniEnv)->SetObjectField(jniEnv, objectInstance, nameID, nameString);

  /* Construct new List */
  clazz = (*jniEnv)->FindClass(jniEnv, "java/util/ArrayList");

  midCtor = (*jniEnv)->GetMethodID(jniEnv, clazz, "<init>", "()V");

  membersVector = (*jniEnv)->NewObject(jniEnv, clazz, midCtor);
  
  if (grp->gr_mem) {

     /* Put me in the vector */
     midAddElement = (*jniEnv)->GetMethodID(jniEnv, clazz, "add", "(Ljava/lang/Object;)Z");

     for (i=0; grp->gr_mem[i]; i++) {
       memberString = (*jniEnv)->NewStringUTF(jniEnv, grp->gr_mem[i]);
       (*jniEnv)->CallBooleanMethod(jniEnv, membersVector, midAddElement, memberString);
     }
  }
  (*jniEnv)->SetObjectField(jniEnv, objectInstance, membersID, membersVector);

}


JNIEXPORT jboolean JNICALL 
Java_eu_unicore_uftp_server_unix_UnixGroup_lookupByName
         (JNIEnv *jniEnv, jobject objectInstance, jstring name)
{
  struct group *grp;
  const char* cName;
  cName = (*jniEnv)->GetStringUTFChars(jniEnv, name, NULL);
  grp = getgrnam(cName);
  (*jniEnv)->ReleaseStringUTFChars(jniEnv, name, cName);

  if (grp == NULL)
    return JNI_FALSE;
  else {
    populateObject(jniEnv, objectInstance, grp);
    return JNI_TRUE;
  }
}


JNIEXPORT jboolean JNICALL 
Java_eu_unicore_uftp_server_unix_UnixGroup_lookupByGid
             (JNIEnv *jniEnv, jobject objectInstance, jint gid)
{
  struct group *grp;
  grp = getgrgid((gid_t)gid);

  if (grp == NULL)
    return JNI_FALSE;
  else {
    populateObject(jniEnv, objectInstance, grp);
    return JNI_TRUE;
  }
}


  
JNIEXPORT jint JNICALL 
Java_de_juelich_fzj_unicore_tsi_unix_UnixGroup_getMembers (JNIEnv * jnienv, 
             jclass j, jint uid) 
{ 
  return((jint)seteuid((uid_t)uid)); 
} 

