#include <jni.h> 
#include <stdio.h> /* for NULL */
#include <pwd.h>
#include <sys/types.h> 
#include <unistd.h> 
#include <grp.h>
#include <eu_unicore_uftp_server_unix_UnixUser.h>

/*
 * author: Valentina Huber, Forschungszentrum Juelich GmbH
 */
                       
/* Fills in a UserInfo object's fields with information from a UNIX password structure. */
static void populateObject(JNIEnv *jniEnv, jobject objectInstance, struct passwd *pwd)
{
  static jclass clazz;
  static jfieldID loginNameID;
  static jfieldID uidID;
  static jfieldID gidID;
  static jfieldID euidID;
  static jfieldID egidID;
  static jfieldID nameID;
  static jfieldID homeID;
  static jfieldID shellID;

  jstring loginNameString;
  jstring nameString;
  jstring homeString;
  jstring shellString;

  /* Find the object's class */
  clazz = (*jniEnv)->GetObjectClass(jniEnv, objectInstance);

  /* Look up each of the fields we're interested in */
  loginNameID = (*jniEnv)->GetFieldID(jniEnv, clazz, "loginName", "Ljava/lang/String;");
  uidID = (*jniEnv)->GetFieldID(jniEnv, clazz, "uid", "I");
  gidID = (*jniEnv)->GetFieldID(jniEnv, clazz, "gid", "I");
  euidID = (*jniEnv)->GetFieldID(jniEnv, clazz, "euid", "I");
  egidID = (*jniEnv)->GetFieldID(jniEnv, clazz, "egid", "I");
  nameID = (*jniEnv)->GetFieldID(jniEnv, clazz, "name", "Ljava/lang/String;");
  homeID = (*jniEnv)->GetFieldID(jniEnv, clazz, "home", "Ljava/lang/String;");
  shellID = (*jniEnv)->GetFieldID(jniEnv, clazz, "shell", "Ljava/lang/String;");

  /* Convert C strings to Java strings */
  loginNameString = (*jniEnv)->NewStringUTF(jniEnv, pwd->pw_name);
  nameString = (*jniEnv)->NewStringUTF(jniEnv, pwd->pw_gecos);
  homeString = (*jniEnv)->NewStringUTF(jniEnv, pwd->pw_dir);
  shellString = (*jniEnv)->NewStringUTF(jniEnv, pwd->pw_shell);

  /* Set the fields in the UnixUser object */
  (*jniEnv)->SetObjectField(jniEnv, objectInstance, loginNameID, loginNameString);
  (*jniEnv)->SetIntField(jniEnv, objectInstance, uidID, pwd->pw_uid);
  (*jniEnv)->SetIntField(jniEnv, objectInstance, gidID, pwd->pw_gid);
  (*jniEnv)->SetObjectField(jniEnv, objectInstance, nameID, nameString);
  (*jniEnv)->SetObjectField(jniEnv, objectInstance, homeID, homeString);
  (*jniEnv)->SetObjectField(jniEnv, objectInstance, shellID, shellString);
 
  /* effective user and group ids */
  (*jniEnv)->SetIntField(jniEnv, objectInstance, euidID, geteuid());
  (*jniEnv)->SetIntField(jniEnv, objectInstance, egidID, getegid());
}


JNIEXPORT jobject JNICALL Java_eu_unicore_uftp_server_unix_UnixUser_whoami
(JNIEnv *jniEnv, jclass clazz)
{
  static jmethodID constructorID;
  jint uid;
  jobject user;
  jthrowable exception;

  /* Look up the constructor method. */
  constructorID = (*jniEnv)->GetMethodID(jniEnv, clazz, "<init>", "(I)V");

  /* Look up our UID */
  uid = getuid();

  /* Call the UnixUser(int) constructor */
  user = (*jniEnv)->NewObject(jniEnv, clazz, constructorID, uid);

  /* See if the constructor threw an exception. If so, catch it. */
  exception = (*jniEnv)->ExceptionOccurred(jniEnv);

  if (exception != NULL) { /* handle the exception */
    (*jniEnv)->ExceptionClear(jniEnv);
    return NULL; /* your UID doesn't exist! */
  }
  return user;
}


JNIEXPORT jboolean JNICALL 
Java_eu_unicore_uftp_server_unix_UnixUser_lookupByLoginName
         (JNIEnv *jniEnv, jobject objectInstance, jstring loginName)
{
  struct passwd *pwd;
  const char* cLoginName;
  cLoginName = (*jniEnv)->GetStringUTFChars(jniEnv, loginName, NULL);
  pwd = getpwnam(cLoginName);
  (*jniEnv)->ReleaseStringUTFChars(jniEnv, loginName, cLoginName);

  if (pwd == NULL)
    return JNI_FALSE;
  else {
    populateObject(jniEnv, objectInstance, pwd);
    return JNI_TRUE;
  }
}


JNIEXPORT jboolean JNICALL 
Java_eu_unicore_uftp_server_unix_UnixUser_lookupByUid
             (JNIEnv *jniEnv, jobject objectInstance, jint uid)
{
  struct passwd *pwd = getpwuid(uid);

  if (pwd == NULL)
    return JNI_FALSE;
  else {
    populateObject(jniEnv, objectInstance, pwd);
    return JNI_TRUE;
  }
}


JNIEXPORT jint JNICALL 
Java_eu_unicore_uftp_server_unix_UnixUser_changeIdentity (JNIEnv * jnienv, 
	     jclass j, jint uid, jint original) 
{ 
  return((jint)setresuid((uid_t)uid, (uid_t)uid, (uid_t)original)); 
} 

  
JNIEXPORT jint JNICALL 
Java_eu_unicore_uftp_server_unix_UnixUser_setUid (JNIEnv * jnienv, 
             jclass j, jint uid) 
{ 
  return((jint)setuid((uid_t)uid)); 
} 


JNIEXPORT jint JNICALL 
Java_eu_unicore_uftp_server_unix_UnixUser_setReUid (JNIEnv * jnienv, 
             jclass j, jint ruid, jint euid) 
{ 
  return((jint)setreuid((uid_t)ruid, (uid_t)euid)); 
} 


JNIEXPORT jint JNICALL 
Java_eu_unicore_uftp_server_unix_UnixUser_setEUid (JNIEnv * jnienv, 
             jclass j, jint uid) 
{ 
  return((jint)seteuid((uid_t)uid)); 
} 


JNIEXPORT jint JNICALL 
Java_eu_unicore_uftp_server_unix_UnixUser_setGid (JNIEnv * jnienv, 
             jclass j, jint gid) 
{ 
  return((jint)setgid((gid_t)gid)); 
} 

JNIEXPORT jint JNICALL 
Java_eu_unicore_uftp_server_unix_UnixUser_setReGid (JNIEnv * jnienv, 
               jclass j, jint rgid, jint egid) 
{ 
  return((jint)setregid((gid_t)rgid, (gid_t)egid)); 
} 


JNIEXPORT jint JNICALL 
Java_eu_unicore_uftp_server_unix_UnixUser_setEGid (JNIEnv * jnienv, 
               jclass j, jint gid) 
{ 
  return((jint)setegid((gid_t)gid)); 
} 

JNIEXPORT jint JNICALL 
Java_eu_unicore_uftp_server_unix_UnixUser_initGroups (JNIEnv * jnienv, 
               jclass j, jstring loginName, jint gid){
  const char* cLoginName;
  cLoginName = (*jnienv)->GetStringUTFChars(jnienv, loginName, NULL);
  return ((jint)initgroups(cLoginName, (gid_t)gid));
}
